package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService.Companion.withAgent
import com.sourcegraph.cody.agent.CommandExecuteParams
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.edit.FixupService.Companion.backgroundThread
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

class DocumentCodeSession(controller: FixupService, editor: Editor) :
    FixupSession(controller, editor) {
  private val logger = Logger.getInstance(DocumentCodeSession::class.java)
  private val project = editor.project!!

  init {
    triggerDocumentCodeAsync()
  }

  override fun getLogger() = logger

  private fun triggerDocumentCodeAsync() {
    // This is called on the EDT, so switch to a background thread.
    backgroundThread {
      withAgent(project) { agent ->
        workAroundUninitializedCodebase(editor)
        val response = agent.server.commandsDocument()
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

  override fun accept() {
    withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(COMMAND_ACCEPT, listOf(taskId!!)))
    }
    finish()
  }

  override fun cancel() {
    withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(COMMAND_CANCEL, listOf(taskId!!)))
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
  override fun diff() {
    // The FixupController issues a vscode.diff command to show the smart diff in the
    // handler for cody.fixup.codelens.diff. TODO: Register a handler in the Agent
    // and send a new RPC to the client to display the diff, maybe just a notification.
    logger.warn("Code Lenses: Show Diff")
  }

  override fun undo() {
    withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(COMMAND_UNDO, listOf(taskId!!)))
    }
    undoEdits()
    finish()
  }

  override fun performInsert(doc: Document, edit: TextEdit) {
    // TODO: Call JetBrains to indent the doc string before inserting.
    super.performInsert(doc, edit)
  }

  override fun dispose() {}
}
