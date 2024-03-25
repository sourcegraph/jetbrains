package com.sourcegraph.cody.chat

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.chat.AgentChatSession.Companion.restoreChatSession
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.history.HistoryService
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

    AgentChatSession.createNew(project) { _ ->
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
              // todo: handle error
              throw Error("Request error")
            }
          } else {
            onSuccess.invoke(result)
          }
        } else {
          // todo: handle error
          throw Error("Request timed out")
        }
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
}
