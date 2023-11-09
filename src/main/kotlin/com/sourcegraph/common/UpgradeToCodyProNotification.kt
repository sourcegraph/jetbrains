package com.sourcegraph.common

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.protocol.RateLimitError
import java.time.Duration
import java.time.OffsetDateTime
import org.apache.commons.lang3.time.DurationFormatUtils

class UpgradeToCodyProNotification private constructor(content: String) :
    Notification("Sourcegraph errors", "Sourcegraph", content, NotificationType.WARNING),
    NotificationFullContent {
  init {
    setIcon(Icons.CodyLogo)
    val upgradeAction: AnAction =
        object : DumbAwareAction("Upgrade") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            // NOT IMPLEMENTED
            expire()
          }
        }
    val learnMoreAction: AnAction =
        object : DumbAwareAction("Learn more") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            // NOT IMPLEMENTED
            expire()
          }
        }
    val dismissAction: AnAction =
        object : DumbAwareAction("Dismiss") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            expire()
          }
        }
    addAction(upgradeAction)
    addAction(learnMoreAction)
    addAction(dismissAction)
  }

  fun show(project: Project?) {
    Notifications.Bus.notify(this)
    notify(project)
  }

  companion object {
    fun create(rateLimitError: RateLimitError): UpgradeToCodyProNotification {
      val quotaString = rateLimitError.limit?.let { " ${rateLimitError.limit}" } ?: ""
      val currentDateTime = OffsetDateTime.now()
      val resetString =
          rateLimitError.retryAfter
              ?.let { Duration.between(currentDateTime, it) }
              ?.let { DurationFormatUtils.formatDurationWords(it.toMillis(), true, true) }
              ?.let { " Usage will reset in $it." }
              ?: ""
      return UpgradeToCodyProNotification(
          "You've used all${quotaString} autcompletions.${resetString}")
    }
  }
}
