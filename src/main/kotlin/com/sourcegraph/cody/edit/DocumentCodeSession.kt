package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService.Companion.withAgent
import com.sourcegraph.cody.agent.CommandExecuteParams
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.edit.FixupService.Companion.backgroundThread
import com.sourcegraph.cody.edit.widget.LensWidgetGroup
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

class DocumentCodeSession(controller: FixupService, editor: Editor) :
    FixupSession(controller, editor) {
  private val logger = Logger.getInstance(DocumentCodeSession::class.java)
  private val project = editor.project!!

  private var lensGroup: LensWidgetGroup? = null

  private val lensActionCallbacks =
      mapOf(
          ACTION_ACCEPT to { accept() },
          ACTION_CANCEL to { cancel() },
          ACTION_RETRY to { retry() },
          ACTION_DIFF to { diff() },
          ACTION_UNDO to { undo() },
      )

  init {
    triggerDocumentCodeAsync()
  }

  override fun getLogger() = logger

  private fun triggerDocumentCodeAsync() {
    withAgent(project) { agent ->
      workAroundUninitializedCodebase(editor)
      addClientListeners(agent)
      val response = agent.server.commandsDocument()

      backgroundThread {
        response
            .handle { result, error ->
              if (error != null || result == null) {
                // TODO: Adapt logic from CodyCompletionsManager.handleError
                logger.warn("Error while generating doc string: $error")
              } else {
                taskId = result.id
              }
              null
            }
            .exceptionally { error: Throwable? ->
              if (!(error is CancellationException || error is CompletionException)) {
                logger.warn("Error while generating doc string: $error")
              }
              null
            }
            .completeOnTimeout(null, 3, TimeUnit.SECONDS)
      }
    }
  }

  // We're consistently triggering the 'retrieved codebase context before initialization' error
  // in ContextProvider.ts. It's a different initialization path from completions & chat.
  // Calling onFileOpened forces the right initialization path.
  private fun workAroundUninitializedCodebase(editor: Editor) {
    val file = FileDocumentManager.getInstance().getFile(editor.document)!!
    CodyAgentCodebase.getInstance(project).onFileOpened(project, file)
  }

  @RequiresEdt
  private fun addClientListeners(agent: CodyAgent) {
    agent.client.setOnDisplayCodeLens { params ->
      if (params.uri != FileDocumentManager.getInstance().getFile(editor.document)?.url) {
        logger.warn("received code lens for wrong document: ${params.uri}")
        return@setOnDisplayCodeLens
      }
      lensGroup?.let { if (!it.isDisposed.get()) Disposer.dispose(it) }
      LensWidgetGroup(this, editor).let {
        synchronized(this) {
          lensGroup = it // Set this first, in case of race conditions.
          it.display(params, lensActionCallbacks)
        }
      }
    }

    // TODO: When and why is this called?
    agent.client.setOnEditTaskDidDelete { task ->
      if (task.id != taskId) {
        logger.warn("onEditTaskDidDelete: $this got wrong task id for task $task")
      }
    }

    agent.client.setOnWorkspaceEdit { params ->
      for (op in params.operations) {
        when (op.type) {
          "create-file" -> logger.warn("Workspace edit operation created a file: ${op.uri}")
          "rename-file" ->
              logger.warn("Workspace edit operation renamed a file: ${op.oldUri} -> ${op.newUri}")
          "delete-file" -> logger.warn("Workspace edit operation deleted a file: ${op.uri}")
          "edit-file" -> {
            if (op.edits == null) {
              logger.warn("Workspace edit operation has no edits")
            } else {
              // TODO: Bring this back when there is a lens group again.
              // lensGroup!!.withListenersMuted { performInlineEdits(op.edits) }
            }
          }
          else ->
              logger.warn(
                  "DocumentCommand session received unknown workspace edit operation: ${op.type}")
        }
      }
    }

    // We get our insertion command via a workspace/edit request above.
    agent.client.setOnTextDocumentEdit { params ->
      logger.warn("DocumentCommand session received text document edit: $params")
    }
  }

  override fun accept() {
    withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(ACTION_ACCEPT, listOf(taskId!!)))
    }
    finish()
  }

  override fun cancel() {
    withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(ACTION_CANCEL, listOf(taskId!!)))
    }
    if (performedEdits) {
      undo()
    } else {
      finish()
    }
  }

  override fun retry() {
    // TODO: The actual prompt is displayed as ghost text in the text input field.
    // E.g. "Write a brief documentation comment for the selected code <etc.>"
    // We need to send the prompt along with the lenses, so that the client can display it.
    EditCommandPrompt(controller, editor, "Edit instructions and Retry").displayPromptUI()
  }

  // Brings up a diff view showing the changes the AI made.
  private fun diff() {
    // The FixupController issues a vscode.diff command to show the smart diff in the
    // handler for cody.fixup.codelens.diff. TODO: Register a handler in the Agent
    // and send a new RPC to the client to display the diff, maybe just a notification.
    logger.warn("Code Lenses: Show Diff")
  }

  fun undo() {
    withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(ACTION_UNDO, listOf(taskId!!)))
    }
    undoEdits()
    finish()
  }

  // The Agent's doc string is not indented, so fix it up.
  // TODO: Maybe just reformat the range we modified, but it needs to be atomically undoable.
  override fun performInsert(doc: Document, edit: TextEdit) {
    super.performInsert(doc, indentDocString(doc, edit))
  }

  override fun dispose() {}

  private fun indentDocString(doc: Document, edit: TextEdit): TextEdit {
    val text = edit.value ?: return edit
    val insertLine = edit.position?.line ?: return edit
    val needsNewline: Boolean
    // Get indentation from the first non-blank line following the doc string insert point.
    val startLine =
        if (doc.getLineStartOffset(insertLine) == doc.getLineEndOffset(insertLine)) {
          val lastLine = doc.getLineNumber(doc.textLength)
          val nonBlank =
              (insertLine + 1..lastLine).firstOrNull { line ->
                doc.getLineStartOffset(line) != doc.getLineEndOffset(line)
              } ?: return edit
          needsNewline = (nonBlank == insertLine)
          nonBlank
        } else {
          needsNewline = true
          insertLine
        }
    val startOffset = doc.getLineStartOffset(startLine)
    var indentEnd = startOffset
    while (indentEnd < doc.getLineEndOffset(startLine)) {
      val c = doc.getText(TextRange(indentEnd, indentEnd + 1))[0]
      if (c != ' ' && c != '\t') {
        break
      }
      indentEnd++
    }
    val indent = doc.getText(TextRange(startOffset, indentEnd))

    val lines = text.split("\n")
    val indentedLines = lines.map { indent + it }
    val newText = indentedLines.joinToString("\n") + if (needsNewline) "\n" else ""

    return TextEdit(type = edit.type, range = edit.range, position = edit.position, value = newText)
  }

  companion object {
    const val ACTION_ACCEPT = "cody.fixup.codelens.accept"
    const val ACTION_CANCEL = "cody.fixup.codelens.cancel"
    const val ACTION_RETRY = "cody.fixup.codelens.retry"
    const val ACTION_DIFF = "cody.fixup.codelens.diff"
    const val ACTION_UNDO = "cody.fixup.codelens.undo"

    // TODO: Register the hotkeys now that we are displaying them.
    fun getHotkey(command: String): String {
      // Claude picked these key bindings for me.
      val mac = SystemInfoRt.isMac
      return when (command) {
        ACTION_ACCEPT -> if (mac) "⌥⌘A" else "Ctrl+Alt+A"
        ACTION_CANCEL -> if (mac) "⌥⌘R" else "Ctrl+Alt+R"
        ACTION_DIFF -> if (mac) "⌘D" else "Ctrl+D" // JB default
        ACTION_RETRY -> if (mac) "⌘Z" else "Ctrl+Z" // JB default
        ACTION_UNDO -> if (mac) "⌥⌘C" else "Alt+Ctrl+C"
        else -> ""
      }
    }
  }
}
