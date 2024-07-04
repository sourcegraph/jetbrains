package com.sourcegraph.cody.chat.actions

import com.intellij.openapi.project.Project
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.chat.AgentChatSession

class NewChatAction : BaseChatAction() {

  override fun doAction(project: Project) {
    CodyAgentService.withAgent(project) { agent ->
      agent.server.chatNew().thenAccept {
        // TODO: Rendezvous with the chat, panel ID that result
        println("Chat ID: ${it.chatId}, panel ID: ${it.panelId}")
      }
    }
  }
}
