package com.sourcegraph.cody.initialization

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefApp
import com.sourcegraph.Icons
import com.sourcegraph.common.BrowserOpener.openInBrowser
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.NotificationGroups

class CodyNewUINotification :
    Notification(
        NotificationGroups.CODY_UPDATES,
        CodyBundle.getString("notifications.new-sidebar.title"),
        CodyBundle.getString("notifications.new-sidebar.detail"),
        NotificationType.IDE_UPDATE),
    NotificationFullContent {

  init {
    icon = Icons.CodyLogo

    addAction(
        object :
            NotificationAction(
                CodyBundle.getString("notifications.new-sidebar.see-whats-coming.text")) {
          override fun actionPerformed(anActionEvent: AnActionEvent, notification: Notification) {
            openInBrowser(
                anActionEvent.project,
                CodyBundle.getString("notifications.new-sidebar.see-whats-coming.link"))
            notification.expire()
          }
        })
    addAction(
        object :
            NotificationAction(
                CodyBundle.getString("notifications.new-sidebar.do-not-show-again")) {
          override fun actionPerformed(anActionEvent: AnActionEvent, notification: Notification) {
            PropertiesComponent.getInstance().setValue(ignoreFlag, true)
            notification.expire()
          }
        })
  }

  companion object {
    private val logger = Logger.getInstance(CodyNewUINotification::class.java)
    private val ignoreFlag: String = CodyBundle.getString("notifications.new-sidebar.ignore")

    fun showIfApplicable(project: Project) {
      try {
        val isSupportedIDEVersion = ApplicationInfo.getInstance().build.baselineVersion >= 232
        if (isSupportedIDEVersion &&
            JBCefApp.isSupported() &&
            !PropertiesComponent.getInstance().getBoolean(ignoreFlag)) {
          CodyNewUINotification().notify(project)
        }
      } catch (e: Exception) {
        logger.warn("Failed to show new UI notification", e)
      }
    }
  }
}
