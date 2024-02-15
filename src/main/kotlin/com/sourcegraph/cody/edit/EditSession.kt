package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor

/**
 * Manages the state machine for inline-edit requests.
 *
 * @param instructions The user's instructions for fixing up the code.
 */
class EditSession(
    editor: Editor,
    val instructions: String,
) : InlineFixupSession(editor) {
  private val logger = Logger.getInstance(EditSession::class.java)

  override fun getLogger() = logger

  private fun sendRequest(prompt: String, model: String) {
    logger.info("Sending inline-edit request: $prompt")
    // TODO: This will be very similar to DocumentCommandSession.sendRequest()
  }
}
