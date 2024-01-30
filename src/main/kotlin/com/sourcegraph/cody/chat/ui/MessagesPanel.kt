package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ContextFile
import com.sourcegraph.cody.agent.protocol.ContextMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ChatUIConstants
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.common.CodyBundle
import javax.swing.JPanel

class MessagesPanel(private val project: Project) :
    JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true)) {
  init {
    addHelloMessage()
  }

  private fun addHelloMessage() {
    val welcomeText = CodyBundle.getString("messages-panel.welcome-text")
    addChatMessageAsComponent(ChatMessage(Speaker.ASSISTANT, welcomeText))
  }

  @RequiresEdt
  fun setMessages(message: List<ChatMessage>) {

    val takeLast = message.takeLast(2)
    if (takeLast.size == 2) {
      if (takeLast[1].text == null) {
        return
      }
    }

    removeAll()
    addHelloMessage()
    message.forEach { addChatMessageAsComponent(it) }

    revalidate()
    repaint()
  }

  @RequiresEdt
  fun addMessages(message: ChatMessage) {
    removeBlinkingCursor()

    addChatMessageAsComponent(message)
    if (message.speaker == Speaker.HUMAN) {
      add(BlinkingCursorComponent.instance)
    }

    revalidate()
    repaint()
  }

  private fun createUsedContextFilesPanel(message: ChatMessage): JPanel? {
    val contextMessages =
        message.contextFiles?.map { contextFile: ContextFile ->
          ContextMessage(Speaker.ASSISTANT, message.text ?: "", contextFile)
        } ?: emptyList()

    if (contextMessages.isEmpty()) {
      // Do nothing when there are no context files. It's normal that some answers have no context
      // files.
      return null
    }

    return ContextFilesMessage(project, contextMessages)
  }

  @RequiresEdt
  fun removeBlinkingCursor() {
    components.find { it is BlinkingCursorComponent }?.let { remove(it) }
  }

  fun registerCancellationToken(cancellationToken: CancellationToken) {
    cancellationToken.onFinished {
      ApplicationManager.getApplication().invokeLater { removeBlinkingCursor() }
    }
  }

  @RequiresEdt
  private fun addComponentToChat(messageContent: JPanel) {
    val wrapperPanel = JPanel()
    wrapperPanel.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    wrapperPanel.add(messageContent, VerticalFlowLayout.TOP)
    add(wrapperPanel)
  }

  @RequiresEdt
  private fun addChatMessageAsComponent(message: ChatMessage) {
    if (message.contextFiles.isNullOrEmpty()) {
      if (message.text != null) {
        addComponentToChat(
            SingleMessagePanel(
                message, project, this, ChatUIConstants.ASSISTANT_MESSAGE_GRADIENT_WIDTH))
      }
    } else {
      createUsedContextFilesPanel(message)?.let(::addComponentToChat)
    }
  }
}
