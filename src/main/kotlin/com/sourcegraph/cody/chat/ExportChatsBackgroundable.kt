package com.sourcegraph.cody.chat

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.ExtensionMessage
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.history.HistoryService

class ExportChatsBackgroundable(project: Project, private val onFinished: (Any) -> Unit) :
    Task.Backgroundable(project, "Exporting chats...", true) {

  var result: Any? = null

  override fun run(indicator: ProgressIndicator) {
    val accountId = CodyAuthenticationManager.instance.getActiveAccount(project)?.id
    val chats =
        HistoryService.getInstance(project)
            .state
            .chats
            .filter { it.accountId == accountId }
            .filter { it.messages.isNotEmpty() }

    val agentChatSession =
        AgentChatSession.createNew(project) {
          ExportChatsService.getInstance(project).reset(it.get())
        }

    chats.forEachIndexed { index, chatState ->
      AgentChatSessionService.getInstance(project).getOrCreateFromState(chatState)
      indicator.fraction = (index / (chats.size + 1.0))
      if (indicator.isCanceled) {
        return
      }
    }

    agentChatSession.sendExtensionMessage(ExtensionMessage(type = ExtensionMessage.Type.HISTORY))

    println("START: result = ExportChatsService.getInstance(project).result.get()")
    result = ExportChatsService.getInstance(project).getChats()
    println("STOP: result = ExportChatsService.getInstance(project).result.get()")
  }

  override fun onFinished() {
    println("onFinished")
    super.onFinished()
    result?.let { onFinished.invoke(it) }
  }
}
