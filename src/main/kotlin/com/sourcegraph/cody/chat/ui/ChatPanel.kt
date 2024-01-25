package com.sourcegraph.cody.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xml.util.XmlStringUtil
import com.sourcegraph.cody.PromptPanel
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.Chat
import com.sourcegraph.cody.chat.CodyChatMessageHistory
import com.sourcegraph.cody.commands.CommandId
import com.sourcegraph.cody.context.ui.EnhancedContextPanel
import com.sourcegraph.cody.ui.ChatScrollPane
import com.sourcegraph.cody.vscode.CancellationToken
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class ChatPanel(private val project: Project, private val panelID: String) :
    JPanel(VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 0, true, false)) {
  private val messagesPanel = MessagesPanel(project)
  private val chatPanel = ChatScrollPane(messagesPanel)

  private val isGenerating = AtomicBoolean(false)
  private val chatMessageHistory = CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY)

  private val contextView: EnhancedContextPanel = EnhancedContextPanel(project)
  private val promptPanel: PromptPanel =
      PromptPanel(chatMessageHistory, ::sendChatMessageUsingInputPromptContent, isGenerating::get)

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

    val lowerPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 0, true, false))
    lowerPanel.add(stopGeneratingButton)
    lowerPanel.add(promptPanel)
    lowerPanel.add(contextView)
    add(lowerPanel, BorderLayout.SOUTH)
  }

  @RequiresEdt
  fun requestFocusOnInputPrompt() {
    promptPanel.textArea.requestFocusInWindow()
    promptPanel.textArea.caretPosition = promptPanel.textArea.document.length
  }

  @RequiresEdt
  private fun sendChatMessageUsingInputPromptContent() {
    val cancellationToken = CancellationToken()

    isGenerating.set(true)
    stopGeneratingButton.isVisible = true
    cancellationToken.onCancellationRequested {
      stopGeneratingButton.isVisible = false
      isGenerating.set(false)
    }

    for (listener in stopGeneratingButton.actionListeners) {
      stopGeneratingButton.removeActionListener(listener)
    }
    stopGeneratingButton.addActionListener { cancellationToken.abort() }

    val text = promptPanel.textArea.text
    chatMessageHistory.messageSent(text)
    sendMessage(project, cancellationToken, text, commandId = null)
    promptPanel.reset()
  }

  @RequiresEdt
  fun sendMessage(
      project: Project,
      cancellationToken: CancellationToken,
      message: String?,
      commandId: CommandId?
  ) {
    val displayText = XmlStringUtil.escapeString(message)
    val humanMessage = ChatMessage(Speaker.HUMAN, message, displayText)
    messagesPanel.addOrUpdateMessage(humanMessage)
    // activateChatTab()

    Chat(panelID)
        .sendMessageViaAgent(
            project,
            cancellationToken,
            messagesPanel::addOrUpdateMessage,
            humanMessage,
            commandId,
            contextView.isEnhancedContextEnabled.get())
  }

  companion object {
    private const val CHAT_MESSAGE_HISTORY_CAPACITY = 100
  }
}
