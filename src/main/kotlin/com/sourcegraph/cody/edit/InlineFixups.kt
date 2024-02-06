package com.sourcegraph.cody.edit

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.config.ConfigUtil.isCodyEnabled
import com.sourcegraph.utils.CodyEditorUtil
import java.util.concurrent.atomic.AtomicReference

/** Controller for commands that allow the LLM to edit the code directly. */
@Service
class InlineFixups {
  private val logger = Logger.getInstance(InlineFixups::class.java)
  private val currentJob = AtomicReference(CancellationToken().apply { abort() })
  private var activeSession: InlineFixupCommandSession? = null
  private var currentModel = "GPT-3.5" // last selected from dropdown

  // The last text the user typed in without saving it, for continuity.
  private var lastPrompt: String = ""

  private fun cancelCurrentSession() {
    activeSession?.cancel()
  }

  private fun setSession(session: InlineFixupCommandSession?) {
    cancelCurrentSession()
    activeSession = session
  }

  // Prompt user for instructions for editing selected code.
  fun startCodeEdit(editor: Editor, where: Caret?) {
    if (!isEligibleForInlineEdit(editor)) return
    where ?: editor.caretModel.primaryCaret
    EditCommandPrompt(editor).displayPromptUI()
  }

  // Generate and insert a doc string for the current code.
  fun documentCode(editor: Editor) {
    // Check eligibility before we send the request, and also when we get the response.
    if (!isEligibleForInlineEdit(editor)) return
    setSession(DocumentCommandSession(editor, resetCancellationToken()))
  }

  fun resetCancellationToken(): CancellationToken {
    currentJob.get().abort()
    return CancellationToken().apply { currentJob.set(this) }
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

  companion object {
    @JvmStatic
    val instance: InlineFixups
      get() = service()
  }
}
