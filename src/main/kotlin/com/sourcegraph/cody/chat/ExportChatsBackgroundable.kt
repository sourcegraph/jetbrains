package com.sourcegraph.cody.chat

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.chat.AgentChatSession.Companion.restoreChatSession
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.initialization.EndOfTrialNotificationScheduler
import java.util.concurrent.TimeUnit

class ExportChatsBackgroundable(
    project: Project,
    private val internalId: String?,
    private val onSuccess: (Any) -> Unit,
    private val onFinished: () -> Unit
) : Task.Backgroundable(project, /* title = */ "Exporting chats...", /* canBeCancelled = */ true) {

  override fun run(indicator: ProgressIndicator) {
    val accountId = CodyAuthenticationManager.instance.getActiveAccount(project)?.id
    val chats =
        HistoryService.getInstance(project)
            .state
            .chats
            .filter { it.accountId == accountId }
            .filter { it.messages.isNotEmpty() }
            .filter { it.internalId != null }
            .filter { chat -> if (internalId != null) chat.internalId == internalId else true }

    CodyAgentService.withAgent(project) { agent ->
      chats.forEachIndexed { index, chatState ->
        restoreChatSession(agent, chatState)
        indicator.fraction = ((index + 1.0) / (chats.size + 1.0))
        if (indicator.isCanceled) {
          return@withAgent
        }
      }

      val result = agent.server.chatExport().completeOnTimeout(null, 15, TimeUnit.SECONDS).get()
      if (indicator.isCanceled) {
        return@withAgent
      }

      if (result != null) {
        if (internalId != null) {
          val singleChatHistory = result.find { it.chatID == internalId }
          if (singleChatHistory != null) {
            onSuccess.invoke(singleChatHistory)
          } else {
            logger.warn("export failed: singleChatHistory is null")

            val notification =
                Notification(
                    "Sourcegraph Cody",
                    "Cody: Chat export failed. Please retry...",
                    "",
                    NotificationType.WARNING)
            notification.notify(project)
          }
        } else {
          onSuccess.invoke(result)
        }
      } else {
        logger.warn("export failed: result is null")

        val notification =
            Notification(
                "Sourcegraph Cody",
                "Cody: Chat export timed out. Please retry...",
                "",
                NotificationType.WARNING)
        notification.notify(project)
      }
    }
  }

  override fun onCancel() {
    super.onCancel()
    onFinished.invoke()
  }

  override fun onFinished() {
    super.onFinished()
    onFinished.invoke()
  }

  companion object {
    private val logger = Logger.getInstance(EndOfTrialNotificationScheduler::class.java)
  }
}
