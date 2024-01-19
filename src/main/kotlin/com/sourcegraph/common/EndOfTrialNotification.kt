package com.sourcegraph.common

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.api.SourcegraphApiRequests
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.common.BrowserOpener.openInBrowser
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EndOfTrialNotification private constructor(title: String, content: String) :
    Notification("Sourcegraph errors", title, content, NotificationType.WARNING),
    NotificationFullContent {
  init {
    icon = Icons.CodyLogo
    val dismissAction: AnAction =
        object : DumbAwareAction("Dismiss") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            hideBalloon()
          }
        }

    val upgradeAction: AnAction =
        object : DumbAwareAction("Upgrade") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            openInBrowser(anActionEvent.project, "https://sourcegraph.com/cody/subscription")
            hideBalloon()
          }
        }
    addAction(upgradeAction)
    addAction(dismissAction)
  }

  companion object {
    fun notify(project: Project) {
      if (checkIfPastEndDate()) {
        //        TODO: Check if user has trial account
        CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
          val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
          if (activeAccountType != null && activeAccountType.isDotcomAccount()) {
            agent.server.isCurrentUserPro().thenApply { isUserPro ->
              if (isUserPro != null && isUserPro) {
                val content = "Update your subscription to continue using Cody Pro"
                val title = "Your Cody Pro trial is has ended"
                ApplicationManager.getApplication().invokeLater {
                  EndOfTrialNotification(title, content).notify(project)
                }
              }
            }
          }
        }
      } else {
        scheduler(project)
      }
    }

    fun scheduler(project: Project) {
      val scheduler = Executors.newScheduledThreadPool(1)
      scheduler.scheduleAtFixedRate(
          {
            if (checkIfPastEndDate()) {
              notify(project)
              scheduler.shutdown()
            }
          },
          0,
          2,
          TimeUnit.HOURS)
    }

    private fun checkIfPastEndDate(): Boolean {
      val endDate = SourcegraphApiRequests.getFreeTrialEndDate()
      return endDate.before(Date())
    }
  }
}
