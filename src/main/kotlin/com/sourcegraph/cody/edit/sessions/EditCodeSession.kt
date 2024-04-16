package com.sourcegraph.cody.edit.sessions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.InlineEditParams
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.edit.EditShowDiffAction.Companion.DOCUMENT_AFTER_DATA_KEY
import com.sourcegraph.cody.edit.EditShowDiffAction.Companion.DOCUMENT_BEFORE_DATA_KEY
import com.sourcegraph.cody.edit.EditShowDiffAction.Companion.EDITOR_DATA_KEY
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
    val editShowDiffAction = ActionManager.getInstance().getAction("cody.editShowDiffAction")

    editShowDiffAction.actionPerformed(
        AnActionEvent(
            /* inputEvent = */ null,
            /* dataContext = */ { dataId ->
              when (dataId) {
                CommonDataKeys.PROJECT.name -> project
                EDITOR_DATA_KEY.name -> editor
                DIFF_SESSION_DATA_KEY.name -> this.createDiffSession()
                else -> null
              }
            },
            /* place = */ ActionPlaces.UNKNOWN,
            /* presentation = */ Presentation(),
            /* actionManager = */ ActionManager.getInstance(),
            /* modifiers = */ 0))
  }

  override fun retry() {
    // TODO: The actual prompt is displayed as ghost text in the text input field.
    // E.g. "Write a brief documentation comment for the selected code <etc.>"
    // We need to send the prompt along with the lenses, so that the client can display it.
    EditCommandPrompt(controller, editor, "Edit instructions and Retry").displayPromptUI()
  }
}
