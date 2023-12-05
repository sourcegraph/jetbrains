package com.sourcegraph.common

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.protocol.RateLimitError
import com.sourcegraph.common.BrowserOpener.openInBrowser

class UpgradeToCodyProNotification private constructor(rateLimitError: RateLimitError) :
    Notification(
        "Sourcegraph errors",
        "Sourcegraph",
        "You've used all${rateLimitError.quotaString()} autocompletion suggestions.${rateLimitError.resetString()}",
        NotificationType.WARNING),
    NotificationFullContent {
  init {
    setIcon(Icons.CodyLogo)
    val learnMoreAction: AnAction =
        object : DumbAwareAction("Learn more") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            openInBrowser(
                anActionEvent.project,
                "https://docs.sourcegraph.com/cody/core-concepts/cody-gateway#rate-limits-and-quotas")
            expire()
          }
        }
    val dismissAction: AnAction =
        object : DumbAwareAction("Dismiss") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            expire()
          }
        }

    val isGa = java.lang.Boolean.getBoolean("cody.isGa")
    // TODO(mikolaj):
    // RFC 872 mentions `feature flag cody-pro: true`
    // the flag should be a factor in whether to show the upgrade option
    if (isGa) {
      if (rateLimitError.upgradeIsAvailable) {
        val upgradeAction: AnAction =
            object : DumbAwareAction("Upgrade") {
              override fun actionPerformed(anActionEvent: AnActionEvent) {
                openInBrowser(anActionEvent.project, "https://sourcegraph.com/cody/subscription")
                expire()
              }
            }
        addAction(upgradeAction)
      }
      val checkUsageAction: AnAction =
          object : DumbAwareAction("Check Usage") {
            override fun actionPerformed(anActionEvent: AnActionEvent) {
              openInBrowser(anActionEvent.project, "https://sourcegraph.com/cody/manage")
              expire()
            }
          }
      addAction(checkUsageAction)
    }

    addAction(learnMoreAction)
    addAction(dismissAction)
  }

  companion object {
    fun create(rateLimitError: RateLimitError): UpgradeToCodyProNotification {
      return UpgradeToCodyProNotification(rateLimitError)
    }

    var isFirstRLEOnAutomaticAutocompletionsShown: Boolean = false
    var autocompleteRateLimitError: Boolean = false
    var chatRateLimitError: Boolean = false
  }
}
