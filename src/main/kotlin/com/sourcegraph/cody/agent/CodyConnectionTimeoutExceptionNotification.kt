package com.sourcegraph.cody.agent

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.sourcegraph.Icons
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.NotificationGroups

class CodyConnectionTimeoutExceptionNotification :
    Notification(
        NotificationGroups.SOURCEGRAPH_ERRORS,
        CodyBundle.getString("notifications.cody-connection-timeout.title"),
        CodyBundle.getString("notifications.cody-connection-timeout.detail"),
        NotificationType.WARNING),
    NotificationFullContent {

  init {
    icon = Icons.CodyLogoSlash

    val action = ActionManager.getInstance().getAction("cody.restartCody")
    addAction(
        object : AnAction(action.templateText) {
          override fun actionPerformed(e: AnActionEvent) {
            expire()
            action.actionPerformed(e)
          }
        })
  }
}
