package com.sourcegraph.cody.commands.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

class CommandsContextMenu {
  companion object {
    fun addCommandsToCodyContextMenu(
        project: Project,
        commands: Map<String, String>,
        executeCommand: (String) -> Unit
    ) {
      val actionManager = ActionManager.getInstance()
      val group = actionManager.getAction("CodyEditorActions") as DefaultActionGroup

      // Loop on recipes and create an action for each new item
      for ((command, title) in commands) {
        val actionId = "cody.command.$command"
        val existingAction = actionManager.getAction(actionId)
        if (existingAction != null) {
          continue
        }
        val action: DumbAwareAction =
            object : DumbAwareAction(title) {
              override fun actionPerformed(e: AnActionEvent) {
                executeCommand(command)
              }
            }
        actionManager.registerAction(actionId, action)
        group.addAction(action)
      }
    }
  }
}
