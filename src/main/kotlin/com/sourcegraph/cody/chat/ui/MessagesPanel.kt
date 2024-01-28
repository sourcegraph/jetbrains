package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ChatUIConstants
import javax.swing.JPanel

class MessagesPanel(private val project: Project) :
    JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true)) {
  init {
    val welcomeText =
        "Hello! I'm Cody. I can write code and answer questions for you. See [Cody documentation](https://sourcegraph.com/docs/cody) for help and tips."
    addChatMessageAsComponent(ChatMessage(Speaker.ASSISTANT, welcomeText))
  }

  @Synchronized
  fun addOrUpdateMessage(message: ChatMessage) {
    ApplicationManager.getApplication().invokeLater {
      if (componentCount > 0) {
        val lastPanel = components.last() as? JPanel
        val lastMessage = lastPanel?.getComponent(0) as? SingleMessagePanel
        if (message.id == lastMessage?.getMessageId()) {
          lastMessage.updateContentWith(message)
        } else {
          addChatMessageAsComponent(message)
        }
      } else {
        addChatMessageAsComponent(message)
      }

      revalidate()
      repaint()
    }
  }

  private fun addComponentToChat(messageContent: JPanel) {
    val wrapperPanel = JPanel()
    wrapperPanel.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    wrapperPanel.add(messageContent, VerticalFlowLayout.TOP)
    add(wrapperPanel)
    revalidate()
    repaint()
  }

  private fun addChatMessageAsComponent(message: ChatMessage) {
    addComponentToChat(
        SingleMessagePanel(
            message, project, this, ChatUIConstants.ASSISTANT_MESSAGE_GRADIENT_WIDTH))
  }
}
