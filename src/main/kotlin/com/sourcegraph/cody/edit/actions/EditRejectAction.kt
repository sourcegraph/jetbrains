package com.sourcegraph.cody.edit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.edit.widget.LensAction.DataKeys.EDIT_ID_DATA_KEY

class EditRejectAction : InlineEditAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    val editId = e.getData(EDIT_ID_DATA_KEY) ?: return
    FixupService.getInstance(project).getActiveSession()?.reject(editId)
  }
}
