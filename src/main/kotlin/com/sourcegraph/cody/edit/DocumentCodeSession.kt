package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService.Companion.withAgent
import com.sourcegraph.cody.agent.CommandExecuteParams
import com.sourcegraph.cody.agent.protocol.EditTask
import java.util.concurrent.CompletableFuture

class DocumentCodeSession(controller: FixupService, editor: Editor) :
    FixupSession(controller, editor) {
  private val logger = Logger.getInstance(DocumentCodeSession::class.java)
  private val project = editor.project!!

  override fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask> {
    return agent.server.commandsDocument()
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

  override fun dispose() {}
}
