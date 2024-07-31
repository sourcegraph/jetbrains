package com.sourcegraph.cody.edit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.agent.protocol.Range

class EditAcceptAction : InlineEditAction() {
  override fun performAction(e: AnActionEvent, project: Project) {
    val range = e.getData(Range.DATA_KEY) ?: return
    FixupService.getInstance(project).getActiveSession()?.accept(range)
  }
}
