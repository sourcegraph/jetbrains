package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ContextMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ChatUIConstants
import java.awt.BorderLayout
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

  //  private fun selectChat(item: ChatState) {
  //    ApplicationManager.getApplication().invokeLater {
  //      removeAll()
  //      addWelcomeMessage()
  //      val chat = HistoryService.getInstance().getChatByPanelId(item.panelId!!)
  //      for (message in chat.messages) {
  //        displayUsedContext(
  //            message.contextFiles.map {
  //              ContextMessage(Speaker.ASSISTANT, "", ContextFile(URI.create(it)))
  //            })
  //        addChatMessageAsComponent(ChatMessage(message.speaker!!, message.text, message.text))
  //      }
  //      CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
  //        val model =
  // agent.server.chatModels(ChatModelsParams(chatId!!)).get().models.first().model
  //        val chatMessages = chat.messages.map { ChatMessage(it.speaker!!, it.text, it.text) }
  //        if (item.replyChatId != null) {
  //          val restoredId =
  //              agent.server
  //                  .chatRestore(ChatRestoreParams(model, chatMessages, item.replyChatId!!))
  //                  .get()
  //          chat.panelId = restoredId
  //          chatId = restoredId
  //        }
  //      }
  //      activateChatTab()
  //    }
  //    // todo add to test plan: when cody_history.xml can't be deserialized by any cause, this
  // will
  //    // freeze CodyToolWindowContent totally - IMO this is long term issue
  //  }

  // MYTODO
  private fun displayUsedContext(contextMessages: List<ContextMessage>) {
    if (contextMessages.isEmpty()) {
      // Do nothing when there are no context files.
      // It's normal that some answers have no context files.
      return
    }
    val contextFilesMessage = ContextFilesMessage(project, contextMessages)
    val messageContentPanel = JPanel(BorderLayout())
    messageContentPanel.add(contextFilesMessage)
    addComponentToChat(messageContentPanel)
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
