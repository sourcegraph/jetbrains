package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.protocol.EditTask
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
) : FixupSession(controller, editor) {
  private val logger = Logger.getInstance(EditSession::class.java)

  override fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask> {
    // TODO: Make the method for editing requests; I think it's defined in bee/
    return agent.server.commandsDocument()
  }

  override fun dispose() {
    // No resources to dispose until we implement this class.
    logger.info("Disposing edit session")
  }

  override fun accept() {
    logger.warn("Accept: Not yet implemented")
  }

  override fun retry() {
    logger.warn("Retry: Not yet implemented")
  }

  override fun cancel() {
    logger.warn("Cancel: Not yet implemented")
  }

  override fun diff() {
    logger.warn("Diff: Not yet implemented")
  }

  override fun undo() {
    logger.warn("Undo: Not yet implemented")
  }

  @Suppress("unused")
  private fun sendRequest(prompt: String, @Suppress("unused_parameter") model: String) {
    logger.info("Sending inline-edit request: $prompt")
    // TODO: This will be very similar to DocumentCommandSession.sendRequest()
  }
}
