package com.sourcegraph.cody.chat

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.sourcegraph.cody.CodyToolWindowFactory
import com.sourcegraph.common.ui.DumbAwareEDTAction

class OpenChatAction : DumbAwareEDTAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    ToolWindowManager.getInstance(project)
        .getToolWindow(CodyToolWindowFactory.TOOL_WINDOW_ID)
        ?.show()
    TODO("NYI, focus the chat thru TypeScript")
  }
}
