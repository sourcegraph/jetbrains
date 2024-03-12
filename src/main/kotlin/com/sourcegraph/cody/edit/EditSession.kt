package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService.Companion.withAgent
import com.sourcegraph.cody.agent.CommandExecuteParams
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.InlineEditParams
import java.util.concurrent.CompletableFuture

/**
 * Manages the state machine for inline-edit requests.
 *
 * @param instructions The user's instructions for fixing up the code.
 */
class EditSession(
    controller: FixupService,
    editor: Editor,
    val instructions: String,
    val model: String,
) : FixupSession(controller, editor) {
  private val logger = Logger.getInstance(EditSession::class.java)
  private val project = editor.project!!

  override fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask> {
    // TODO: Make the method for editing requests; I think it's defined in bee/
    val params = InlineEditParams(instructions, model)
    return agent.server.commandsEdit(params)
  }

  override fun dispose() {
    // No resources to dispose until we implement this class.
    logger.info("Disposing edit session")
  }

  override fun accept() {
    logger.warn("Accept: Not yet implemented")
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

  override fun diff() {
    logger.warn("Diff: Not yet implemented")
  }

  override fun retry() {
    // TODO: The actual prompt is displayed as ghost text in the text input field.
    // E.g. "Write a brief documentation comment for the selected code <etc.>"
    // We need to send the prompt along with the lenses, so that the client can display it.
    EditCommandPrompt(controller, editor, "Edit instructions and Retry").displayPromptUI()
  }

  override fun undo() {
    withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(COMMAND_UNDO, listOf(taskId!!)))
    }
    undoEdits()
    finish()
  }
}
