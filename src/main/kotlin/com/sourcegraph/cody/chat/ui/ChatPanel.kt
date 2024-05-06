package com.sourcegraph.cody.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.PromptPanel
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.agent.protocol.ModelUsage
import com.sourcegraph.cody.chat.ChatSession
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.context.ui.EnhancedContextPanel
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.state.LLMState
import com.sourcegraph.cody.ui.ChatScrollPane
import com.sourcegraph.cody.vscode.CancellationToken
import java.awt.BorderLayout
import com.intellij.ui.JBColor
import com.intellij.ui.JBColor.namedColor
import com.jgoodies.forms.factories.Borders.EmptyBorder
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel


class ChatPanel(
    val project: Project,
    val chatSession: ChatSession,
    chatModelProviderFromState: ChatModelsResponse.ChatModelProvider?
) : JPanel(VerticalFlowLayout(VerticalFlowLayout.CENTER, 0, 0, true, false)) {

  private val chatSplitter = OnePixelSplitter(true, 0.9f)


  val promptPanel: PromptPanel = PromptPanel(project, chatSession)
  private val llmDropdown =
      LlmDropdown(
          ModelUsage.CHAT,
          project,
          onSetSelectedItem = ::setLlmForAgentSession,
          parentDialog = null,
          chatModelProviderFromState)
  private val messagesPanel = MessagesPanel(project, chatSession)
  private val chatPanel = ChatScrollPane(messagesPanel)
  private val promptWrapper = JBPanelWithEmptyText(BorderLayout())

  private val contextView: EnhancedContextPanel = EnhancedContextPanel.create(project, chatSession)

  private val stopGeneratingButton =
      object : JButton("Stop generating", IconUtil.desaturate(AllIcons.Actions.Suspend)) {
        init {
          isVisible = false
          layout = FlowLayout(FlowLayout.CENTER, 0, 0)
          minimumSize = Dimension(Short.MAX_VALUE.toInt(), 0)
          isOpaque = false
        }
      }


  init {
    layout = BorderLayout()
    border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

    val contextContainer = JBScrollPane(contextView)
    contextContainer.border = BorderFactory.createEmptyBorder()

    // this wrapper is needed to apply padding. Can't apply padding directly to the promptPanel.
    // Also it makes it aligned with the default padding of the stopGeneratingButton
    val textAreaWrapper = JPanel(BorderLayout())
    textAreaWrapper.border = BorderFactory.createEmptyBorder(4, 4, 0, 4)
    textAreaWrapper.add(promptPanel)


    promptWrapper.background = Color.GREEN
      promptWrapper.border = BorderFactory.createCompoundBorder(
            //adds separator at the top
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            //adds padding
            BorderFactory.createEmptyBorder(8, 4, 8, 4)
        )
        promptWrapper.add(textAreaWrapper, BorderLayout.SOUTH)
        promptWrapper.add(stopGeneratingButton, BorderLayout.NORTH)


    val topWrapper = JPanel(BorderLayout())
      topWrapper.add(llmDropdown, BorderLayout.NORTH)
      topWrapper.add(chatPanel, BorderLayout.CENTER)
      topWrapper.add(promptWrapper, BorderLayout.SOUTH)


    chatSplitter.firstComponent = topWrapper
    chatSplitter.secondComponent = contextContainer
    add(chatSplitter, BorderLayout.CENTER)


    //debug
    //bottomWrapper.border = BorderFactory.createLineBorder(Color.RED, 1)
   // topWrapper.border = BorderFactory.createLineBorder(Color.PINK, 1)
  }

  fun setAsActive() {
    contextView.setContextFromThisChatAsDefault()
    promptPanel.focus()
  }

  fun isEnhancedContextEnabled(): Boolean = contextView.isEnhancedContextEnabled

  @RequiresEdt
  fun addOrUpdateMessage(message: ChatMessage, index: Int) {
    if (messagesPanel.componentCount == 1) {
      llmDropdown.updateAfterFirstMessage()
      promptPanel.updateEmptyTextAfterFirstMessage()
    }
    messagesPanel.addOrUpdateMessage(message, index)
  }

  @RequiresEdt
  fun registerCancellationToken(cancellationToken: CancellationToken) {
    messagesPanel.registerCancellationToken(cancellationToken)
    promptPanel.registerCancellationToken(cancellationToken)

    cancellationToken.onFinished { stopGeneratingButton.isVisible = false }

    stopGeneratingButton.isVisible = true
    for (listener in stopGeneratingButton.actionListeners) {
      stopGeneratingButton.removeActionListener(listener)
    }
    stopGeneratingButton.addActionListener { cancellationToken.abort() }
  }

  private fun setLlmForAgentSession(chatModelProvider: ChatModelsResponse.ChatModelProvider) {
    val activeAccountType = CodyAuthenticationManager.getInstance(project).getActiveAccount()
    if (activeAccountType?.isEnterpriseAccount() == true) {
      // no need to send the webview message since the chat model is set by default
    } else {
      chatSession.sendWebviewMessage(
          WebviewMessage(command = "chatModel", model = chatModelProvider.model))
    }

    HistoryService.getInstance(project)
        .updateChatLlmProvider(
            chatSession.getInternalId(), LLMState.fromChatModel(chatModelProvider))
  }
}
