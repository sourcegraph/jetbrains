package com.sourcegraph.find

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

class OpenFindAction : AnAction(), DumbAware {
  override fun actionPerformed(event: AnActionEvent) {
    event.project?.service<FindService>()?.showPopup()
  }
}
