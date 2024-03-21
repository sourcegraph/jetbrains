package com.sourcegraph.cody.chat

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ChatHistoryResponse
import com.sourcegraph.cody.agent.protocol.IdParam
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.history.HistoryService
import java.util.concurrent.TimeUnit

class ExportChatsBackgroundable(
    project: Project,
    private val onSuccess: (ChatHistoryResponse) -> Unit,
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

    chats.forEachIndexed { index, chatState ->
      AgentChatSessionService.getInstance(project).getOrCreateFromState(chatState)
      indicator.fraction = ((index + 1.0) / (chats.size + 1.0))
      if (indicator.isCanceled) {
        return
      }
    }

    AgentChatSession.createNew(project) { connectionId ->
      CodyAgentService.withAgent(project) { agent ->
        val result: ChatHistoryResponse? =
            agent.server
                .chatExport(IdParam(connectionId))
                .completeOnTimeout(null, 15, TimeUnit.SECONDS)
                .get()
        if (result != null) {
          onSuccess.invoke(result)
        } else {
          throw Error("Request timed out") // todo: handle it
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
