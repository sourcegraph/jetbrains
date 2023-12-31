package com.sourcegraph.common

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.protocol.GetFeatureFlag
import com.sourcegraph.cody.agent.protocol.RateLimitError
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.common.BrowserOpener.openInBrowser
import com.sourcegraph.common.CodyBundle.fmt
import java.util.concurrent.atomic.AtomicReference

class UpgradeToCodyProNotification
private constructor(title: String, content: String, shouldShowUpgradeOption: Boolean) :
    Notification("Sourcegraph errors", title, content, NotificationType.WARNING),
    NotificationFullContent {
  init {
    icon = Icons.CodyLogo
    val learnMoreAction: AnAction =
        object : DumbAwareAction("Learn more") {
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
    val dismissAction: AnAction =
        object : DumbAwareAction("Dismiss") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            hideBalloon()
          }
        }

    if (shouldShowUpgradeOption) {
      val upgradeAction: AnAction =
          object : DumbAwareAction("Upgrade") {
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
      ApplicationManager.getApplication().executeOnPooledThread {
        val codyProJetbrains = isCodyProJetbrains(project)
        val shouldShowUpgradeOption = codyProJetbrains && rateLimitError.upgradeIsAvailable
        val content =
            when {
              shouldShowUpgradeOption ->
                  CodyBundle.getString("UpgradeToCodyProNotification.content.upgrade")
                      .fmt(rateLimitError.limit?.let { " $it" } ?: "")
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
    }

    @RequiresEdt
    fun isCodyProJetbrains(project: Project): Boolean {
      val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
      if (activeAccountType != null && activeAccountType.isDotcomAccount()) {
        val server = CodyAgent.getServer(project)
        if (server != null) {
          val codyProFeatureFlag = server.evaluateFeatureFlag(GetFeatureFlag("CodyProJetBrains"))
          return codyProFeatureFlag.get()!!
        }
      }
      return false
    }

    var isFirstRLEOnAutomaticAutocompletionsShown: Boolean = false
    var autocompleteRateLimitError: AtomicReference<RateLimitError?> = AtomicReference(null)
    var chatRateLimitError: AtomicReference<RateLimitError?> = AtomicReference(null)
  }
}
