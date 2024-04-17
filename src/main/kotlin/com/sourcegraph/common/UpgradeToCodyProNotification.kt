package com.sourcegraph.common

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.protocol.RateLimitError
import com.sourcegraph.common.BrowserOpener.openInBrowser
import com.sourcegraph.common.ui.DumbAwareBGTAction
import com.sourcegraph.common.ui.SimpleDumbAwareBGTAction
import java.util.concurrent.atomic.AtomicReference

class UpgradeToCodyProNotification
private constructor(title: String, content: String, shouldShowUpgradeOption: Boolean) :
    Notification(NotificationGroups.SOURCEGRAPH_ERRORS, title, content, NotificationType.WARNING),
    NotificationFullContent {
  init {
    icon = Icons.CodyLogo
    val learnMoreAction: AnAction =
        object : DumbAwareBGTAction("Learn more") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            val learnMoreLink =
                when {
                  shouldShowUpgradeOption -> "https://sourcegraph.com/cody/subscription"
                  else ->
                      "https://sourcegraph.com/docs/cody/core-concepts/cody-gateway#rate-limits-and-quotas"
                }
            openInBrowser(anActionEvent.project, learnMoreLink)
            hideBalloon()
          }
        }
    val dismissAction: AnAction = SimpleDumbAwareBGTAction("Dismiss") { hideBalloon() }

    if (shouldShowUpgradeOption) {
      val upgradeAction: AnAction =
          object : DumbAwareBGTAction("Upgrade") {
            override fun actionPerformed(anActionEvent: AnActionEvent) {
              openInBrowser(anActionEvent.project, "https://sourcegraph.com/cody/subscription")
              hideBalloon()
            }
          }
      addAction(upgradeAction)
    }

    addAction(learnMoreAction)
    addAction(dismissAction)
  }

  companion object {
    fun notify(rateLimitError: RateLimitError, project: Project) {

      val shouldShowUpgradeOption = rateLimitError.upgradeIsAvailable
      val content =
          when {
            shouldShowUpgradeOption ->
                CodyBundle.getString("UpgradeToCodyProNotification.content.upgrade")
            else -> CodyBundle.getString("UpgradeToCodyProNotification.content.explain")
          }
      val title =
          when {
            shouldShowUpgradeOption ->
                CodyBundle.getString("UpgradeToCodyProNotification.title.upgrade")
            else -> CodyBundle.getString("UpgradeToCodyProNotification.title.explain")
          }

      UpgradeToCodyProNotification(title, content, shouldShowUpgradeOption).notify(project)
    }

    var isFirstRLEOnAutomaticAutocompletionsShown: Boolean = false
    var autocompleteRateLimitError: AtomicReference<RateLimitError?> = AtomicReference(null)
    var chatRateLimitError: AtomicReference<RateLimitError?> = AtomicReference(null)
  }
}
