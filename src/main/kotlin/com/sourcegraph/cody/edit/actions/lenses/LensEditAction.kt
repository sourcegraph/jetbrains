package com.sourcegraph.cody.edit.actions.lenses

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.TaskIdParam
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.common.CodyBundle

abstract class LensEditAction(val editAction: (CodyAgent, TaskIdParam) -> Unit) :
    AnAction(), DumbAware {
  private val logger = Logger.getInstance(LensEditAction::class.java)

  fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(event: AnActionEvent) {
    val project = event.project ?: return
    val hasActiveAccount = CodyAuthenticationManager.getInstance(project).hasActiveAccount()
    event.presentation.isEnabled = hasActiveAccount
    if (!event.presentation.isEnabled) {
      event.presentation.description =
          CodyBundle.getString("action.sourcegraph.disabled.description")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    try {
      var project = e.project
      if (project == null) {
        project = e.dataContext.getData(PlatformDataKeys.PROJECT.name) as? Project
      }
      if (project == null || project.isDisposed) {
        logger.warn("Received code lens action for null or disposed project: $e")
        return
      }

      val taskId = e.dataContext.getData(TASK_ID_KEY)
      if (taskId == null) {
        logger.warn("No taskId found in data context for action ${this.javaClass.name}: $e")
        return
      }

      CodyAgentService.withAgent(project) { editAction(it, TaskIdParam(taskId)) }
    } catch (ex: Exception) {
      // Don't show error lens here; it's sort of pointless.
      logger.warn("Error accepting edit accept task: $ex")
    }
  }

  companion object {
    val TASK_ID_KEY: DataKey<String> = DataKey.create("TASK_ID_KEY")
  }
}
