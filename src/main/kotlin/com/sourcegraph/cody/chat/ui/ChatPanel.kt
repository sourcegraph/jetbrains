package com.sourcegraph.cody.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xml.util.XmlStringUtil
import com.jetbrains.rd.util.AtomicReference
import com.sourcegraph.cody.PromptPanel
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.Chat
import com.sourcegraph.cody.chat.CodyChatMessageHistory
import com.sourcegraph.cody.context.ui.EnhancedContextPanel
import com.sourcegraph.cody.ui.ChatScrollPane
import com.sourcegraph.cody.vscode.CancellationToken
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.concurrent.CompletableFuture
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class ChatPanel(private val project: Project, private val panelId: String) :
    JPanel(VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 0, true, false)) {
  private val messagesPanel = MessagesPanel(project)
  private val chatPanel = ChatScrollPane(messagesPanel)

  private val requestToken = AtomicReference(CancellationToken())
  private val promptMessageHistory = CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY)

  private val contextView: EnhancedContextPanel = EnhancedContextPanel(project)
  private val promptPanel: PromptPanel =
      PromptPanel(
          promptMessageHistory,
          ::sendChatMessageUsingInputPromptContent,
          requestToken.get()::isDone)

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
    // Initially there is no activity, it needs to be started by user
    requestToken.get().dispose()

    layout = BorderLayout()
    border = BorderFactory.createEmptyBorder(0, 0, 0, 10)
    add(chatPanel, BorderLayout.CENTER)

    val lowerPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 10, 10, true, false))
    lowerPanel.add(stopGeneratingButton)
    lowerPanel.add(promptPanel)
    lowerPanel.add(contextView)
    add(lowerPanel, BorderLayout.SOUTH)
  }

  fun getRequestToken(): CancellationToken = requestToken.get()

  @RequiresEdt
  fun addOrUpdateMessage(message: ChatMessage) {
    messagesPanel.addOrUpdateMessage(message)
  }

  @RequiresEdt
  fun requestFocusOnInputPrompt() {
    promptPanel.textArea.requestFocusInWindow()
    promptPanel.textArea.caretPosition = promptPanel.textArea.document.length
  }

  private fun registerNewChatInteraction(request: CompletableFuture<*>) {
    val previousRequest = requestToken.getAndSet(CancellationToken())
    previousRequest.abort()

    requestToken.get().onCancellationRequested { request.cancel(true) }
    requestToken.get().onFinished { stopGeneratingButton.isVisible = false }

    ApplicationManager.getApplication().invokeLater {
      stopGeneratingButton.isVisible = true
      for (listener in stopGeneratingButton.actionListeners) {
        stopGeneratingButton.removeActionListener(listener)
      }
      stopGeneratingButton.addActionListener { requestToken.get().abort() }
    }
  }

  @RequiresEdt
  private fun sendChatMessageUsingInputPromptContent() {
    val text = promptPanel.textArea.text
    promptMessageHistory.messageSent(text)

    val displayText = XmlStringUtil.escapeString(text)
    val humanMessage = ChatMessage(Speaker.HUMAN, text, displayText)
    messagesPanel.addOrUpdateMessage(humanMessage)

    registerNewChatInteraction(
        Chat.sendMessageViaAgent(
            project, panelId, humanMessage, contextView.isEnhancedContextEnabled.get()))

    promptPanel.reset()
  }

  companion object {
    private const val CHAT_MESSAGE_HISTORY_CAPACITY = 100
  }
}
