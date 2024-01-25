package com.sourcegraph.cody.chat

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.sourcegraph.cody.CodyToolWindowContent

class NewChatAction : DumbAwareAction() {

  override fun actionPerformed(event: AnActionEvent) {
    if (event.project == null) return

    CodyToolWindowContent.executeOnInstanceIfNotDisposed(event.project!!) { refreshChatToEmpty() }
  }

  override fun update(event: AnActionEvent) {
    if (event.project == null) return
    CodyToolWindowContent.executeOnInstanceIfNotDisposed(event.project!!) {
      event.presentation.isVisible = isChatVisible
    }
  }
}
