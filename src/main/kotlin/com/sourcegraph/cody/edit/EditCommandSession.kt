package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.vscode.CancellationToken

/**
 * Manages the state machine for inline-edit requests.
 *
 * @param instructions The user's instructions for fixing up the code.
 */
class EditCommandSession(
    editor: Editor,
    val instructions: String,
    cancellationToken: CancellationToken
) : InlineFixupCommandSession(editor, cancellationToken) {
  private val logger = Logger.getInstance(EditCommandSession::class.java)

  override fun cancel() {
    TODO("Not yet implemented")
  }

  override fun getLogger() = logger

  private fun sendRequest(prompt: String, model: String) {
    logger.info("Sending inline-edit request: $prompt")
    // TODO: This will be very similar to DocumentCommandSession.sendRequest()
  }
}
