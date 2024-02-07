package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ChatSession
import com.sourcegraph.cody.chat.ChatUIConstants
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.common.CodyBundle
import javax.swing.JPanel

class MessagesPanel(private val project: Project, private val chatSession: ChatSession) :
    JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true)) {
  init {
    val welcomeText = CodyBundle.getString("messages-panel.welcome-text")
    addChatMessageAsComponent(ChatMessage(Speaker.ASSISTANT, source = "chat", welcomeText, id = -1))
  }

  @RequiresEdt
  fun addOrUpdateMessage(message: ChatMessage, shouldAddBlinkingCursor: Boolean) {
    removeBlinkingCursor()

    val messageToUpdate = components.getOrNull(message.id + 1).let { it as? JPanel }
    if (messageToUpdate != null) {
      val singleMessagePanel = messageToUpdate.getComponent(0) as? SingleMessagePanel
      val contextFilesPanel = messageToUpdate.getComponent(1) as? ContextFilesPanel
      singleMessagePanel?.updateContentWith(message)
      contextFilesPanel?.updateContentWith(message.contextFiles)
    } else {
      addChatMessageAsComponent(message)
    }

    if (shouldAddBlinkingCursor && message.speaker == Speaker.HUMAN) {
      add(BlinkingCursorComponent.instance)
    }

    revalidate()
    repaint()
  }

  @RequiresEdt
  fun removeBlinkingCursor() {
    components.find { it is BlinkingCursorComponent }?.let { remove(it) }
  }

  fun registerCancellationToken(cancellationToken: CancellationToken) {
    cancellationToken.onFinished {
      ApplicationManager.getApplication().invokeLater {
        removeBlinkingCursor()
        getLastMessage()?.onPartFinished()
      }
    }
  }

  @RequiresEdt
  private fun addChatMessageAsComponent(message: ChatMessage) {
    val singleMessagePanel =
        SingleMessagePanel(
            message, project, this, ChatUIConstants.ASSISTANT_MESSAGE_GRADIENT_WIDTH, chatSession)
    val contextFilesPanel = ContextFilesPanel(project, message)
    val wrapper = JPanel()
    wrapper.add(singleMessagePanel)
    wrapper.add(contextFilesPanel)
    wrapper.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    add(wrapper)
  }

  private fun getLastMessage(): SingleMessagePanel? {
    val lastPanel = components.last() as? JPanel
    return lastPanel?.getComponent(0) as? SingleMessagePanel
  }
}
