package com.sourcegraph.cody.autocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.AutocompleteParams
import com.sourcegraph.cody.agent.protocol.AutocompleteResult
import com.sourcegraph.cody.agent.protocol.AutocompleteTriggerKind
import com.sourcegraph.cody.agent.protocol.ErrorCode
import com.sourcegraph.cody.agent.protocol.ErrorCodeUtils.toErrorCode
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument.Companion.uriFor
import com.sourcegraph.cody.agent.protocol.RateLimitError.Companion.toRateLimitError
import com.sourcegraph.cody.agent.protocol.SelectedCompletionInfo
import com.sourcegraph.cody.agent.protocol_extensions.Position
import com.sourcegraph.cody.agent.protocol_generated.Position
import com.sourcegraph.cody.agent.protocol_generated.Range
import com.sourcegraph.cody.ignore.ActionInIgnoredFileNotification
import com.sourcegraph.cody.ignore.IgnoreOracle
import com.sourcegraph.cody.ignore.IgnorePolicy
import com.sourcegraph.cody.statusbar.CodyStatus
import com.sourcegraph.cody.statusbar.CodyStatusService.Companion.notifyApplication
import com.sourcegraph.cody.statusbar.CodyStatusService.Companion.resetApplication
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.cody.vscode.InlineCompletionTriggerKind
import com.sourcegraph.cody.vscode.IntelliJTextDocument
import com.sourcegraph.common.UpgradeToCodyProNotification
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.utils.CodyEditorUtil.isImplicitAutocompleteEnabledForEditor
import com.sourcegraph.utils.ThreadingUtil
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException

@JvmInline
value class InlineCompletionProviderID(val id: String)

class CodyInlineCompletionProvider : InlineCompletionProvider {
  private val logger = Logger.getInstance(CodyInlineCompletionProvider::class.java)
  private val currentJob = AtomicReference(CancellationToken())
  val id = InlineCompletionProviderID("Cody")

  suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
    val editor = request.editor
    val project = editor.project ?: return InlineCompletionSuggestion.empty()
    if (!isImplicitAutocompleteEnabledForEditor(editor)) {
      return InlineCompletionSuggestion.empty()
    }
    val lookupString: String? = null // todo: can we use this provider for lookups?

    cancelCurrentJob(project)
    val cancellationToken = CancellationToken()
    currentJob.set(cancellationToken)

    val completions =
        ThreadingUtil.runInEdtAndGet {
          fetchCompletions(
                  project,
                  editor,
                  InlineCompletionTriggerKind.AUTOMATIC,
                  cancellationToken,
                  lookupString)
              .get()
        } ?: return InlineCompletionSuggestion.empty()

    val offset = request.endOffset
    val lineNumber = editor.document.getLineNumber(offset)
    val caretPositionInLine = offset - editor.document.getLineStartOffset(lineNumber)

    return InlineCompletionSuggestion.withFlow {
      completions.items
          .map { InlineCompletionGrayTextElement(it.insertText.substring(caretPositionInLine)) }
          .forEach { emit(it) }
    }
  }

  @RequiresEdt
  private fun fetchCompletions(
      project: Project,
      editor: Editor,
      triggerKind: InlineCompletionTriggerKind,
      cancellationToken: CancellationToken,
      lookupString: String?,
  ): CompletableFuture<AutocompleteResult?> {

    val textDocument = IntelliJTextDocument(editor, project)
    val offset = editor.caretModel.offset
    val position = textDocument.positionAt(offset)
    val lineNumber = editor.document.getLineNumber(offset)
    val caretPositionInLine = offset - editor.document.getLineStartOffset(lineNumber)
    val originalText = editor.document.getText(TextRange(offset - caretPositionInLine, offset))

    var startPosition = 0
    if (!lookupString.isNullOrEmpty()) {
      startPosition = findLastCommonSuffixElementPosition(originalText, lookupString)
    }

    val virtualFile =
        FileDocumentManager.getInstance().getFile(editor.document)
            ?: return CompletableFuture.completedFuture(null)
    val params =
        if (lookupString.isNullOrEmpty())
            AutocompleteParams(
                uriFor(virtualFile),
                Position(position.line, position.character),
                if (triggerKind == InlineCompletionTriggerKind.INVOKE)
                    AutocompleteTriggerKind.INVOKE.value
                else AutocompleteTriggerKind.AUTOMATIC.value)
        else
            AutocompleteParams(
                uriFor(virtualFile),
                Position(position.line, position.character),
                AutocompleteTriggerKind.AUTOMATIC.value,
                SelectedCompletionInfo(
                    lookupString,
                    if (startPosition < 0) Range(position, position)
                    else Range(Position(lineNumber, startPosition), position)))
    notifyApplication(project, CodyStatus.AutocompleteInProgress)

    val resultOuter = CompletableFuture<AutocompleteResult?>()
    CodyAgentService.withAgent(project) { agent ->
      if (triggerKind == InlineCompletionTriggerKind.INVOKE &&
          IgnoreOracle.getInstance(project).policyForUri(virtualFile.url, agent).get() !=
              IgnorePolicy.USE) {
        ActionInIgnoredFileNotification.maybeNotify(project)
        resetApplication(project)
        resultOuter.cancel(true)
      } else {
        val completions = agent.server.autocompleteExecute(params)

        // Important: we have to `.cancel()` the original `CompletableFuture<T>` from lsp4j. As soon
        // as we use `thenAccept()` we get a new instance of `CompletableFuture<Void>` which does
        // not correctly propagate the cancellation to the agent.
        cancellationToken.onCancellationRequested { completions.cancel(true) }

        ApplicationManager.getApplication().executeOnPooledThread {
          completions
              .handle { result, error ->
                if (error != null) {
                  if (triggerKind == InlineCompletionTriggerKind.INVOKE ||
                      !UpgradeToCodyProNotification.isFirstRLEOnAutomaticAutocompletionsShown) {
                    handleError(project, error)
                  }
                } else if (result != null && result.items.isNotEmpty()) {
                  UpgradeToCodyProNotification.isFirstRLEOnAutomaticAutocompletionsShown = false
                  UpgradeToCodyProNotification.autocompleteRateLimitError.set(null)
                  resultOuter.complete(result)
                }
                null
              }
              .exceptionally { error: Throwable? ->
                if (!(error is CancellationException || error is CompletionException)) {
                  logger.warn("failed autocomplete request $params", error)
                }
                null
              }
              .completeOnTimeout(null, 3, TimeUnit.SECONDS)
              .thenRun { // This is a terminal operation, so we needn't call get().
                resetApplication(project)
                resultOuter.complete(null)
              }
        }
      }
    }
    cancellationToken.onCancellationRequested { resultOuter.cancel(true) }
    return resultOuter
  }

  private fun handleError(project: Project, error: Throwable?) {
    if (error is ResponseErrorException) {
      if (error.toErrorCode() == ErrorCode.RateLimitError) {
        val rateLimitError = error.toRateLimitError()
        UpgradeToCodyProNotification.autocompleteRateLimitError.set(rateLimitError)
        UpgradeToCodyProNotification.isFirstRLEOnAutomaticAutocompletionsShown = true
        ApplicationManager.getApplication().executeOnPooledThread {
          UpgradeToCodyProNotification.notify(error.toRateLimitError(), project)
        }
      }
    }
  }

  private fun cancelCurrentJob(project: Project?) {
    currentJob.get().abort()
    project?.let { resetApplication(it) }
  }

  private fun findLastCommonSuffixElementPosition(
      stringToFindSuffixIn: String,
      suffix: String
  ): Int {
    var i = 0
    while (i <= suffix.length) {
      val partY = suffix.substring(0, suffix.length - i)
      if (stringToFindSuffixIn.endsWith(partY)) {
        return stringToFindSuffixIn.length - (suffix.length - i)
      }
      i++
    }
    return 0
  }

  fun isEnabled(event: InlineCompletionEvent): Boolean {
    return ConfigUtil.isCodyEnabled() && ConfigUtil.isCodyAutocompleteEnabled()
  }

  override suspend fun getProposals(request: InlineCompletionRequest): List<InlineCompletionElement> {
    TODO("Not yet implemented")
  }

  override fun isEnabled(event: DocumentEvent): Boolean {
    TODO("Not yet implemented")
  }
}
