package com.sourcegraph.cody.edit.sessions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.InlineEditParams
import com.sourcegraph.cody.edit.FixupService
import java.util.concurrent.CompletableFuture

/**
 * Manages the state machine for inline-edit requests.
 *
 * @param instructions The user's instructions for fixing up the code.
 */
class EditCodeSession(
    controller: FixupService,
    editor: Editor,
    val instructions: String,
    private val chatModelProvider: ChatModelsResponse.ChatModelProvider,
) : FixupSession(controller, editor.project!!, editor) {

  override fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask> {
    return try {
      agent.server.commandsEdit(InlineEditParams(instructions, chatModelProvider.model))
    } catch (x: Exception) {
      logger.warn("Error making editCommands/edit request", x)
      CompletableFuture.failedFuture(x)
    }
  }

  companion object {
    private val logger = Logger.getInstance(EditCodeSession::class.java)
  }
}
