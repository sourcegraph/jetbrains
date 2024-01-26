package com.sourcegraph.common

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.CurrentUserCodySubscription
import com.sourcegraph.cody.agent.protocol.GetFeatureFlag
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.common.BrowserOpener.openInBrowser
import com.sourcegraph.config.ConfigUtil
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EndOfTrialNotification
private constructor(title: String, content: String, actionLink: String) :
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
        object : DumbAwareAction(CodyBundle.getString("EndOfTrialNotification.link-action-name")) {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            openInBrowser(anActionEvent.project, actionLink)
            hideBalloon()
          }
        }
    addAction(upgradeAction)
    addAction(dismissAction)
  }

  companion object {
    private fun showEndOfTrialNotificationIfApplicable(
        project: Project,
        currentUserCodySubscription: CurrentUserCodySubscription,
        codyProTrialEnded: Boolean,
        useSscForCodySubscription: Boolean
    ) {
      val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
      if (activeAccountType != null && activeAccountType.isDotcomAccount()) {

        if (currentUserCodySubscription.plan == "PRO" &&
            currentUserCodySubscription.status == "PENDING" &&
            useSscForCodySubscription) {
          val (title, content, link) =
              if (codyProTrialEnded) {
                Triple(
                    CodyBundle.getString("EndOfTrialNotification.ended.title"),
                    CodyBundle.getString("EndOfTrialNotification.ended.content"),
                    CodyBundle.getString("EndOfTrialNotification.ended.link"))
              } else {
                Triple(
                    CodyBundle.getString("EndOfTrialNotification.ending-soon.title"),
                    CodyBundle.getString("EndOfTrialNotification.ending-soon.content"),
                    CodyBundle.getString("EndOfTrialNotification.ending-soon.link"))
              }

          EndOfTrialNotification(title, content, link).notify(project)
        }
      }
    }

    fun startScheduler(project: Project) {
      val scheduler = Executors.newScheduledThreadPool(1)
      scheduler.scheduleAtFixedRate(
          {
            if (!ConfigUtil.isCodyEnabled()) {
              return@scheduleAtFixedRate
            }

            CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
              agent.server
                  .getCurrentUserCodySubscription()
                  .thenApply {
                    if (it == null) {
                      // todo: proper handling
                      println("getCurrentUserCodySubscription returned null")
                      return@thenApply
                    }

                    agent.server
                        .evaluateFeatureFlag(GetFeatureFlag.CodyProTrialEnded)
                        .completeOnTimeout(false, 4, TimeUnit.SECONDS)
                        .thenCombine(
                            agent.server
                                .evaluateFeatureFlag(GetFeatureFlag.UseSscForCodySubscription)
                                .completeOnTimeout(false, 4, TimeUnit.SECONDS)) {
                                codyProTrialEnded,
                                useSscForCodySubscription ->
                              showEndOfTrialNotificationIfApplicable(
                                  project,
                                  currentUserCodySubscription = it,
                                  codyProTrialEnded ?: false,
                                  useSscForCodySubscription ?: false)
                              scheduler.shutdown()
                            }
                  }
                  .completeOnTimeout(null, 4, TimeUnit.SECONDS)
            }
          },
          0,
          2,
          TimeUnit.HOURS)
    }
  }
}
