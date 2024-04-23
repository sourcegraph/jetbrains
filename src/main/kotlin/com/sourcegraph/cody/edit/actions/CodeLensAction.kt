package com.sourcegraph.cody.edit.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

abstract class CodeLensAction : AnAction() {
  private val logger = Logger.getInstance(CodeLensAction::class.java)

  abstract fun performAction(e: AnActionEvent, project: Project)

  override fun actionPerformed(e: AnActionEvent) {
    var project = e.project
    if (project == null) {
      project = e.dataContext.getData(PlatformDataKeys.PROJECT.name) as? Project
    }
    if (project == null || project.isDisposed) {
      logger.warn("Received code lens action for null or disposed project: $e")
      return
    }
    performAction(e, project)
  }
}
