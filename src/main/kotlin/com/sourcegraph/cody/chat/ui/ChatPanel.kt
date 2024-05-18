package com.sourcegraph.cody.chat.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.sourcegraph.cody.PromptPanel
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.agent.protocol.ModelUsage
import com.sourcegraph.cody.chat.ChatSession
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.context.ui.EnhancedContextPanel
import com.sourcegraph.cody.context.ui.MAX_REMOTE_REPOSITORY_COUNT
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.state.LLMState
import com.sourcegraph.cody.ui.ChatScrollPane
import com.sourcegraph.cody.ui.CollapsibleTitledSeparator
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.CodyBundle.fmt
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel

class ChatPanel(
    val project: Project,
    val chatSession: ChatSession,
    chatModelProviderFromState: ChatModelsResponse.ChatModelProvider?
) : JPanel(VerticalFlowLayout(VerticalFlowLayout.CENTER, 0, 0, true, false)) {

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

  internal val contextView: EnhancedContextPanel = EnhancedContextPanel.create(project, chatSession)

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
    border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    add(chatPanel, BorderLayout.CENTER)



    val lowerPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 10, 10, true, false))
    lowerPanel.add(stopGeneratingButton)
    lowerPanel.add(promptPanel)
    lowerPanel.background = JBColor.namedColor("Editor.SearchField.background", JBColor.WHITE)
    lowerPanel.border = JBUI.Borders.customLine(JBColor.namedColor("background"),0,1, 0, 0)
    //lowerPanel.add(contextView)




    val wrapper = JPanel()
    wrapper.add(llmDropdown)
    wrapper.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 12, 12, true, false)

    add(lowerPanel, BorderLayout.SOUTH)
    add(wrapper, BorderLayout.NORTH)



    // Create a frame
    val frame = JPanel()
    frame.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    frame.background = JBColor.namedColor("Editor.SearchField.background", JBColor.WHITE)
    contextView.background = JBColor.namedColor("Editor.SearchField.background", JBColor.WHITE)

    // Create an instance of CollapsibleTitledSeparator
    val separator = CollapsibleTitledSeparator("Context Details")

    // Create a panel to show when expanded
    val innerExpantion = JPanel()
    innerExpantion.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    innerExpantion.border = BorderFactory.createEmptyBorder(0, 8, 0, 0)
    innerExpantion.add(contextView)
    innerExpantion.background = JBColor.namedColor("Editor.SearchField.background", JBColor.WHITE)

    // Initially set the panel visibility based on the separator's expanded state
    innerExpantion.isVisible = separator.expanded

    // Add a listener to toggle the panel visibility when the separator is collapsed/expanded
    separator.onAction { isExpanded ->
      innerExpantion.isVisible = isExpanded
    }

    // Add the separator and panel to the frame
    frame.add(separator)
    frame.add(innerExpantion)

    lowerPanel.add(frame)

    HelpTooltip()
      .setTitle(CodyBundle.getString("context-panel.tree.help-tooltip.title"))
      .setDescription(
        CodyBundle.getString("context-panel.tree.help-tooltip.description")
          .fmt(MAX_REMOTE_REPOSITORY_COUNT.toString()))
      .setLink(CodyBundle.getString("context-panel.tree.help-tooltip.link.text")) {
        BrowserUtil.open(CodyBundle.getString("context-panel.tree.help-tooltip.link.href"))
      }
      .setLocation(HelpTooltip.Alignment.LEFT)
      .setInitialDelay(
        800) // Tooltip can interfere with the treeview, so cool off on showing it.
      .installOn(separator)


    frame.isVisible = true
  }

  fun setAsActive() {
    contextView.setContextFromThisChatAsDefault()
    promptPanel.focus()
  }

  fun isEnhancedContextEnabled(): Boolean = contextView.isEnhancedContextEnabled

  @RequiresEdt
  fun addOrUpdateMessage(message: ChatMessage, index: Int) {
    val numberOfMessagesBeforeAddOrUpdate = messagesPanel.componentCount
    if (numberOfMessagesBeforeAddOrUpdate == 1) {
      llmDropdown.updateAfterFirstMessage()
      promptPanel.updateEmptyTextAfterFirstMessage()
    }
    messagesPanel.addOrUpdateMessage(message, index)
    if (numberOfMessagesBeforeAddOrUpdate < messagesPanel.componentCount) {
      chatPanel.touchingBottom = true
    }
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
