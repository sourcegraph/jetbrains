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
import com.sourcegraph.cody.agent.protocol.CurrentUserCodySubscription
import com.sourcegraph.cody.agent.protocol.GetFeatureFlag
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
          val (title, content) =
              if (codyProTrialEnded) {
                Pair(
                    CodyBundle.getString("EndOfTrialNotification.ended.title"),
                    CodyBundle.getString("EndOfTrialNotification.ended.content"))
              } else {
                Pair(
                    CodyBundle.getString("EndOfTrialNotification.ending-soon.title"),
                    CodyBundle.getString("EndOfTrialNotification.ending-soon.content"))
              }

          ApplicationManager.getApplication().invokeLater {
            EndOfTrialNotification(title, content).notify(project)
          }
        }
      }
    }

    fun startScheduler(project: Project) {
      val scheduler = Executors.newScheduledThreadPool(1)
      scheduler.scheduleAtFixedRate(
          {
            CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
              agent.server
                  .getCurrentUserCodySubscription()
                  .thenApply {
                    if (it == null) {
                      // todo: proper handling
                      println("getCurrentUserCodySubscription returned null")
                      return@thenApply
                    }

                    val shouldShowTheNotification = it.currentPeriodEndAt.before(Date())
                    if (shouldShowTheNotification) {

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
                                    it,
                                    codyProTrialEnded ?: false,
                                    useSscForCodySubscription ?: false)
                                scheduler.shutdown()
                              }
                    }
                  }
                  .completeOnTimeout(null, 4, TimeUnit.SECONDS)
            }
          },
          0,
          2,
          TimeUnit.HOURS)
    }

    // todo: consider alternative solution using GraphQL directly
    //    private fun checkIfPastEndDate(accessToken: String, progressIndicator: ProgressIndicator):
    // Boolean {
    //      val endDate = SourcegraphApiRequests.CurrentUser(
    //              SourcegraphApiRequestExecutor.Factory.instance.create(accessToken),
    // progressIndicator)
    //              .getDetails(server).currentUserCodySubscription!!.currentPeriodEndAt
    //      return endDate.before(Date())
    //    }
  }
}
