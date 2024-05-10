package com.sourcegraph.cody.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
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
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.border.Border


class PromptWrapper : JPanel(BorderLayout()) {
  // Define the default background color as a property
  private val defaultBackground = JBColor.namedColor("Editor.SearchField.background", JBColor.WHITE)

  init {
    isOpaque = true
    super.setBackground(defaultBackground)
    border = BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
      BorderFactory.createEmptyBorder(8, 4, 8, 4)
    )
  }

  // Override setBackground to control background setting
  override fun setBackground(bg: Color?) {

    super.setBackground(defaultBackground)
    // }
  }
}

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

    /** Layout:
     * ┏━━━━━━━━━━━━topWrapper━━━━━━━━━━━━┓
     * ┃  ┌─────────chatPanel──────────┐  ┃
     * ┃  └────────────────────────────┘  ┃
     * ┃  ┏━━━━━━━promptWrapper━━━━━━━━┓  ┃
     * ┃  ┃ ┌──stopGeneratingButton──┐ ┃  ┃
     * ┃  ┃ └────────────────────────┘ ┃  ┃
     * ┃  ┃ ┏━━━━textAreaWrapper━━━━━┓ ┃  ┃
     * ┃  ┃ ┃┌─────promptPanel─────┐ ┃ ┃  ┃
     * ┃  ┃ ┃└─────────────────────┘ ┃ ┃  ┃
     * ┃  ┃ ┗━━━━━━━━━━━━━━━━━━━━━━━━┛ ┃  ┃
     * ┃  ┃ ┌──────llmDropdown───────┐ ┃  ┃
     * ┃  ┃ └────────────────────────┘ ┃  ┃
     * ┃  ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛  ┃
     * ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
     *    ─ ─ ─ ─ ─chatSplitter ─ ─ ─ ─ ─
     *  ┌────────contextContainer─────────┐
     *  └─────────────────────────────────┘
     */

    layout = BorderLayout()
    border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

    // This wrapper is needed to apply padding. Can't apply padding directly to the promptPanel.
    // Also, it makes it aligned with the default padding of the stopGeneratingButton
    val textAreaWrapper = JPanel(BorderLayout())
      textAreaWrapper.add(promptPanel)
      textAreaWrapper.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)

    val promptWrapper = PromptWrapper()
      promptWrapper.add(textAreaWrapper, BorderLayout.CENTER)
      promptWrapper.add(stopGeneratingButton, BorderLayout.NORTH)
      promptWrapper.add(llmDropdown, BorderLayout.SOUTH)

    //val llmDropdownWrapper = JPanel(BorderLayout())
      //llmDropdownWrapper.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
      //llmDropdownWrapper.add(llmDropdown, BorderLayout.NORTH)

    val topWrapper = JPanel(BorderLayout())
//topWrapper.add(llmDropdownWrapper, BorderLayout.NORTH)
topWrapper.add(chatPanel, BorderLayout.CENTER)
topWrapper.add(promptWrapper, BorderLayout.SOUTH)



    val contextContainer = JBScrollPane(contextView)
    contextContainer.border = BorderFactory.createEmptyBorder()
    chatSplitter.firstComponent = topWrapper
    chatSplitter.secondComponent = contextContainer
    add(chatSplitter, BorderLayout.CENTER)

    //debug
    //promptWrapper.border = BorderFactory.createLineBorder(Color.RED, 1)
    //topWrapper.border = BorderFactory.createLineBorder(Color.RED, 1)
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
