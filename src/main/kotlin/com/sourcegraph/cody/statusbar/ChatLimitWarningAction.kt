package com.sourcegraph.cody.statusbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.sourcegraph.config.ConfigUtil

class ChatLimitWarningAction :
    DumbAwareAction("<html><b>Warning:</b> Chat Limit Reached...</html>") {
  override fun actionPerformed(e: AnActionEvent) {

    Messages.showDialog(
        e.project,
        "You've used all messages. The allowed number of request per day is limited at the moment to ensure the service stays functional.",
        "Chat Limit Reached",
        arrayOf("Ok"),
        /* defaultOptionIndex= */ 0,
        Messages.getWarningIcon())
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = ConfigUtil.isCodyEnabled()
  }
}
