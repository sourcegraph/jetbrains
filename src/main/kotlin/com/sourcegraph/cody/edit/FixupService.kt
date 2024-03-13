package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.sourcegraph.config.ConfigUtil.isCodyEnabled
import com.sourcegraph.utils.CodyEditorUtil

/** Controller for commands that allow the LLM to edit the code directly. */
@Service
class FixupService : Disposable {
  private val logger = Logger.getInstance(FixupService::class.java)
  private var activeSessions: MutableMap<String, FixupSession> = mutableMapOf()
  private var currentModel = "GPT-3.5" // last selected from dropdown

  // The last text the user typed in without saving it, for continuity.
  private var lastPrompt: String = ""

  // Prompt user for instructions for editing selected code.
  fun startCodeEdit(editor: Editor) {
    if (!isEligibleForInlineEdit(editor)) return

    EditCommandPrompt(editor, "Edit Code with Cody").displayPromptUI()
  }

  // Generate and insert a doc string for the current code.
  fun documentCode(editor: Editor) {
    // Check eligibility before we send the request, and also when we get the response.
    if (isEligibleForInlineEdit(editor)) {
      addSession(DocumentCodeSession(editor))
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

  fun getCurrentModel(): String = currentModel

  fun setCurrentModel(model: String) {
    currentModel = model
  }

  fun getLastPrompt(): String = lastPrompt

  private fun addSession(session: FixupSession) {
    //activeSessions[session.id] = session
  }

  companion object {
    @JvmStatic
    val instance: FixupService
      get() = service()

    fun backgroundThread(code: Runnable) {
      ApplicationManager.getApplication().executeOnPooledThread(code)
    }
  }

  override fun dispose() {
    //addSession(null)
  }
}
