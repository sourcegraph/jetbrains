package com.sourcegraph.cody.edit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.CodyToolWindowContent.Companion.logger
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.edit.widget.LensAction.DataKeys.EDIT_ID_DATA_KEY

class EditAcceptAction : InlineEditAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    val editId = e.getData(EDIT_ID_DATA_KEY) ?: return
    logger.warn("JM: Calling accept from performAction. editId: $editId")
    FixupService.getInstance(project).getActiveSession()?.accept(editId)
  }
}
