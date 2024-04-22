package com.sourcegraph.cody.edit

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

// This file contains a bunch of tiny dispatcher classes for Inline Edits.
// They provide a bridge between our fake code-lens widgets and IntelliJ's
// action system, which supports hotkeys, integration tests, and such.

abstract class CodeLensAction : AnAction() {
  private val logger = Logger.getInstance(CodeLensAction::class.java)

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

  abstract fun performAction(e: AnActionEvent, project: Project)
}

class EditCancelAction : CodeLensAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    FixupService.getInstance(project).getActiveSession()?.cancel()
  }
}

class EditAcceptAction : CodeLensAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    FixupService.getInstance(project).getActiveSession()?.accept()
  }
}

class EditRetryAction : CodeLensAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    FixupService.getInstance(project).getActiveSession()?.retry()
  }
}

class EditDiffAction : CodeLensAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    FixupService.getInstance(project).getActiveSession()?.diff()
  }
}

class EditUndoAction : CodeLensAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    FixupService.getInstance(project).getActiveSession()?.undo()
  }
}
