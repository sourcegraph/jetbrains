package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.config.ConfigUtil.isCodyEnabled
import com.sourcegraph.utils.CodyEditorUtil

/** Controller for commands that allow the LLM to edit the code directly. */
@Service(Service.Level.PROJECT)
class FixupService(val project: Project) : Disposable {
  private val logger = Logger.getInstance(FixupService::class.java)

  // We only use this for multiplexing task updates from the Agent to concurrent sessions.
  // TODO: Consider doing the multiplexing in CodyAgentClient instead.
  private var activeSessions: MutableMap<String, FixupSession> = mutableMapOf()

  private var lastSelectedModel = "GPT-3.5"

  // The last text the user typed in without saving it, for continuity.
  private var lastPrompt: String = ""

  init {
    // JetBrains docs say avoid heavy lifting in the constructor, so pass to another thread.
    // TODO: Ensure the listeners are added before the user can perform any actions in an Editor.
    backgroundThread {
      CodyAgentService.withAgent(project) { agent ->
        ApplicationManager.getApplication().invokeLater {
          agent.client.setOnEditTaskDidUpdate { task -> activeSessions[task.id]?.update(task) }

          agent.client.setOnEditTaskDidDelete { task -> TODO("What is this for again? $task") }

          agent.client.setOnWorkspaceEdit { params ->
            for (op in params.operations) {
              when (op.type) {
                "create-file" -> {
                  logger.warn("Workspace edit operation created a file: ${op.uri}")
                }
                "rename-file" -> {
                  logger.warn(
                      "Workspace edit operation renamed a file: ${op.oldUri} -> ${op.newUri}")
                }
                "delete-file" -> {
                  logger.warn("Workspace edit operation deleted a file: ${op.uri}")
                }
                "edit-file" -> {
                  if (op.edits == null) {
                    logger.warn("Workspace edit operation has no edits")
                  } else {
                    // TODO: Associate task ID with a workspace edit.
                    // Right now we're just using the first active inline-edit session we find.
                    activeSessions.values.firstOrNull()?.performInlineEdits(op.edits)
                  }
                }
                else ->
                    logger.warn(
                        "DocumentCommand session received unknown workspace edit operation: ${op.type}")
              }
            }
          }
        }
      }
    }
  }

  /** Entry point for the inline edit command, called by the action handler. */
  fun startCodeEdit(editor: Editor) {
    if (isEligibleForInlineEdit(editor)) {
      EditCommandPrompt(this, editor, "Edit Code with Cody").displayPromptUI()
    }
  }

  /** Entry point for the document code command, called by the action handler. */
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
    val id = session.taskId
    if (id == null) {
      logger.warn("Session has no ID")
    } else {
      activeSessions[id] = session
    }
  }

  fun removeSession(session: FixupSession) {
    activeSessions.remove(session.taskId)
  }

  override fun dispose() {
    activeSessions.values.forEach { it.dispose() }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FixupService {
      return project.service<FixupService>()
    }

    fun backgroundThread(code: Runnable) {
      ApplicationManager.getApplication().executeOnPooledThread(code)
    }
  }
}
