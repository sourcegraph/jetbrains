package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor

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

  override fun getLogger() = logger

  override fun dispose() {
    // No resources to dispose until we implement this class.
    logger.info("Disposing edit session")
  }

  override fun accept() {
    TODO("Not yet implemented")
  }

  override fun retry() {
    logger.warn("retrying EditSession")
  }

  override fun cancel() {
    logger.warn("cancelling EditSession")
  }

  @Suppress("unused")
  private fun sendRequest(prompt: String, @Suppress("unused_parameter") model: String) {
    logger.info("Sending inline-edit request: $prompt")
    // TODO: This will be very similar to DocumentCommandSession.sendRequest()
  }
}
