package com.sourcegraph.cody.edit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.edit.FixupService

/**
 * Programmatically dispatches the key sequence for either opening the Edit Code dialog, or if the
 * action lens group is being displayed, delegating to the undo action. Similarly, closes the
 * Error lens group if showing.
 */
class EditCancelOrUndoAction : InlineEditAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    val session = FixupService.getInstance(project).getActiveSession() ?: return
    when {
      session.isShowingActionLens() -> EditUndoAction().actionPerformed(e)
      session.isShowingErrorLens() -> EditDismissAction().actionPerformed(e)
      else -> EditCancelAction().actionPerformed(e)
    }
  }
}
