package com.sourcegraph.cody.autocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.InlineCompletionSuggestion
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.protocol.AutocompleteResult
import com.sourcegraph.cody.statusbar.CodyStatusService.Companion.resetApplication
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.cody.vscode.InlineCompletionTriggerKind
import com.sourcegraph.cody.vscode.IntelliJTextDocument
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.utils.CodyEditorUtil.getTextRange
import com.sourcegraph.utils.CodyEditorUtil.isImplicitAutocompleteEnabledForEditor
import com.sourcegraph.utils.CodyFormatter
import com.sourcegraph.utils.ThreadingUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

@JvmInline value class InlineCompletionProviderID(val id: String)

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

    return InlineCompletionSuggestion.withFlow {
      completions.items
          .firstNotNullOfOrNull {
            val range = getTextRange(editor.document, it.range)
            val originalText = editor.document.getText(range)
            val cursorOffsetInOriginalText = offset - range.startOffset

            val formattedCompletionText: String =
                if (System.getProperty("cody.autocomplete.enableFormatting") == "false") {
                  it.insertText
                } else {
                  WriteCommandAction.runWriteCommandAction<String>(
                      editor.project,
                      {
                        CodyFormatter.formatStringBasedOnDocument(
                            it.insertText, project, editor.document, range, offset)
                      })
                }

            // ...

            val originalTextBeforeCursor = originalText.substring(0, cursorOffsetInOriginalText)
            val originalTextAfterCursor = originalText.substring(cursorOffsetInOriginalText)
            val completionText =
                formattedCompletionText
                    .removePrefix(originalTextBeforeCursor)
                    .removeSuffix(originalTextAfterCursor)
            if (completionText.trim().isBlank()) {
              null
            } else {
              InlineCompletionGrayTextElement(completionText)
            }
          }
          ?.let { emit(it) }
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
    val lineNumber = editor.document.getLineNumber(offset)
    val caretPositionInLine = offset - editor.document.getLineStartOffset(lineNumber)
    val originalText = editor.document.getText(TextRange(offset - caretPositionInLine, offset))

    val result = CompletableFuture<AutocompleteResult?>()
    Utils.triggerAutocompleteAsync(
        project,
        editor,
        offset,
        textDocument,
        triggerKind,
        cancellationToken,
        lookupString,
        originalText,
        logger) { autocompleteResult ->
          result.complete(autocompleteResult)
        }
    return result
  }

  private fun cancelCurrentJob(project: Project?) {
    currentJob.get().abort()
    project?.let { resetApplication(it) }
  }

  //  fun restartOn(event: InlineCompletionEvent): Boolean {
  //    return event is InlineCompletionEvent.InlineLookupEvent
  //  }

  fun isEnabled(event: InlineCompletionEvent): Boolean {
    return isEnabled()
  }

  override suspend fun getProposals(
      request: InlineCompletionRequest
  ): List<InlineCompletionElement> {
    return emptyList()
  }

  override fun isEnabled(event: DocumentEvent): Boolean {
    return isEnabled()
  }

  private fun isEnabled(): Boolean {
    val ideVersion = ApplicationInfo.getInstance().build.baselineVersion
    val isRemoteDev = ClientSessionsManager.getAppSession()?.isRemote ?: false
    return ideVersion >= 233 &&
        isRemoteDev &&
        ConfigUtil.isCodyEnabled() &&
        ConfigUtil.isCodyAutocompleteEnabled()
  }
}
