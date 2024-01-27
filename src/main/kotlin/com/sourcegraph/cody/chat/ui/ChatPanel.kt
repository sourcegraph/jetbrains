package com.sourcegraph.cody.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xml.util.XmlStringUtil
import com.sourcegraph.cody.PromptPanel
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ChatSession
import com.sourcegraph.cody.chat.CodyChatMessageHistory
import com.sourcegraph.cody.context.ui.EnhancedContextPanel
import com.sourcegraph.cody.ui.ChatScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class ChatPanel(project: Project, private val chatSession: ChatSession) :
    JPanel(VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 0, true, false)) {
  private val messagesPanel = MessagesPanel(project)
  private val chatPanel = ChatScrollPane(messagesPanel)
  private val promptMessageHistory = CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY)
  private val promptPanel: PromptPanel =
      PromptPanel(promptMessageHistory, ::sendChatMessageUsingInputPromptContent, chatSession)
  private val contextView: EnhancedContextPanel = EnhancedContextPanel(project)

  private val stopGeneratingButton =
      object : JButton("Stop generating", IconUtil.desaturate(AllIcons.Actions.Suspend)) {
        init {
          isVisible = false
          layout = FlowLayout(FlowLayout.CENTER)
          minimumSize = Dimension(Short.MAX_VALUE.toInt(), 0)
          isOpaque = false
        }
      }

  init {
    layout = BorderLayout()
    border = BorderFactory.createEmptyBorder(0, 0, 0, 10)
    add(chatPanel, BorderLayout.CENTER)

    val lowerPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 10, 10, true, false))
    lowerPanel.add(stopGeneratingButton)
    lowerPanel.add(promptPanel)
    lowerPanel.add(contextView)
    add(lowerPanel, BorderLayout.SOUTH)
  }

  fun isEnhancedContextEnabled(): Boolean = contextView.isEnhancedContextEnabled.get()

  fun addOrUpdateMessage(message: ChatMessage) {
    ApplicationManager.getApplication().invokeLater { messagesPanel.addOrUpdateMessage(message) }
  }

  @RequiresEdt
  fun requestFocusOnInputPrompt() {
    promptPanel.textArea.requestFocusInWindow()
    promptPanel.textArea.caretPosition = promptPanel.textArea.document.length
  }

  @RequiresEdt
  private fun sendChatMessageUsingInputPromptContent() {
    val text = promptPanel.textArea.text
    promptMessageHistory.messageSent(text)
    promptPanel.reset()

    val displayText = XmlStringUtil.escapeString(text)
    val humanMessage = ChatMessage(Speaker.HUMAN, text, displayText)
    messagesPanel.addOrUpdateMessage(humanMessage)

    val cancellationToken = chatSession.sendMessage(humanMessage)
    cancellationToken.onFinished { stopGeneratingButton.isVisible = false }

    stopGeneratingButton.isVisible = true
    for (listener in stopGeneratingButton.actionListeners) {
      stopGeneratingButton.removeActionListener(listener)
    }
    stopGeneratingButton.addActionListener { cancellationToken.abort() }
  }

  companion object {
    private const val CHAT_MESSAGE_HISTORY_CAPACITY = 100
  }
}
