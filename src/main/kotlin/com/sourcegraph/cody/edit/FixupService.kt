package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.CodyTaskState
import com.sourcegraph.config.ConfigUtil.isCodyEnabled
import com.sourcegraph.utils.CodyEditorUtil

/**
 * Controller for commands that allow the LLM to edit the code directly.
 */
@Service(Service.Level.PROJECT)
class FixupService(val project: Project) : Disposable {
  private val logger = Logger.getInstance(FixupService::class.java)

  private var activeSessions: MutableMap<String, FixupSession> = mutableMapOf()

  private var lastSelectedModel = "GPT-3.5"

  // The last text the user typed in without saving it, for continuity.
  private var lastPrompt: String = ""

  init {
    // JetBrains docs say avoid heavy lifting in the constructor, so pass to another thread.
    // TODO: Ensure the listener is added before the user can perform any actions in an Editor.
    backgroundThread {
      CodyAgentService.withAgent(project) { agent ->
        agent.client.setOnEditTaskDidUpdate { task ->
          val session = activeSessions[task.id] ?: return@setOnEditTaskDidUpdate
          session.update(task)
        }
      }
    }
  }

  /**
   * Entry point for the inline edit command, called by the action handler.
   */
  fun startCodeEdit(editor: Editor) {
    if (isEligibleForInlineEdit(editor)) {
      EditCommandPrompt(this, editor, "Edit Code with Cody").displayPromptUI()
    }
  }

  /**
   * Entry point for the document code command, called by the action handler.
   */
  fun startDocumentCode(editor: Editor) {
    if (isEligibleForInlineEdit(editor)) {
      addSession(DocumentCodeSession(this, editor))
    }
  }

  fun isEligibleForInlineEdit(editor: Editor): Boolean {
    if (!isCodyEnabled()) {
      logger.warn("Edit code invoked when Cody not enabled")
      return false
    }
    if (!CodyEditorUtil.isEditorValidForAutocomplete(editor)) {
      logger.warn("Inline edit invoked when editing not available")
      return false
    }
    return true
  }

  // TODO: get model list from protocol
  fun getModels(): List<String> = listOf("GPT-4", "GPT-3.5")

  fun getCurrentModel(): String = lastSelectedModel

  fun setCurrentModel(model: String) {
    lastSelectedModel = model
  }

  fun getLastPrompt(): String = lastPrompt

  fun addSession(session: FixupSession) {
    //activeSessions[session.id] = session
  }

  fun removeSession(session: FixupSession) {

  }

  companion object {

    fun backgroundThread(code: Runnable) {
      ApplicationManager.getApplication().executeOnPooledThread(code)
    }
  }

  override fun dispose() {
    // Nothing to dispose.
  }
}
