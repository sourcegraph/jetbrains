package com.sourcegraph.cody.edit.sessions

import com.intellij.diff.DiffManager
import com.intellij.diff.requests.NoDiffRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupRenderer
import com.intellij.openapi.vcs.ex.Range
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
    project: Project,
    document: Document,
    val instructions: String,
    private val chatModelProvider: ChatModelsResponse.ChatModelProvider,
) : FixupSession(controller, editor, project, document) {
  override fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask> {
    val params = InlineEditParams(instructions, chatModelProvider.model)
    return agent.server.commandsEdit(params)
  }

  override fun dispose() {}

  override fun diff() {
    val diffRequest = NoDiffRequest.INSTANCE
    DiffManager.getInstance().showDiff(project, diffRequest)
    LineStatusMarkerPopupRenderer.ShowLineStatusRangeDiffAction(editor, Range(0, 0 ,0 , 0))
  }

  override fun retry() {
    // TODO: The actual prompt is displayed as ghost text in the text input field.
    // E.g. "Write a brief documentation comment for the selected code <etc.>"
    // We need to send the prompt along with the lenses, so that the client can display it.
    EditCommandPrompt(controller, editor, "Edit instructions and Retry").displayPromptUI()
  }
}
