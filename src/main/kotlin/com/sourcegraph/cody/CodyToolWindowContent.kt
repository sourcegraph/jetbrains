package com.sourcegraph.cody

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xml.util.XmlStringUtil
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.*
import com.sourcegraph.cody.chat.*
import com.sourcegraph.cody.commands.ui.CommandsTabPanel
import com.sourcegraph.cody.config.CodyAccount
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.context.ui.EnhancedContextPanel
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.ui.HistoryTree
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.ui.ChatScrollPane
import com.sourcegraph.cody.ui.SendButton
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.telemetry.GraphQlLogger
import java.awt.*
import java.awt.event.ActionEvent
import java.net.URI
import java.util.*
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
class CodyToolWindowContent(private val project: Project) {
  private val allContentLayout = CardLayout()
  val allContentPanel = JPanel(allContentLayout)
  private val tabbedPane = JBTabbedPane()
  private val messagesPanel = JPanel()
  private val promptPanel: PromptPanel
  private val subscriptionPanel: SubscriptionTabPanel
  private val sendButton: JButton
  private var inProgressChat = CancellationToken()
  private val stopGeneratingButton =
      JButton("Stop generating", IconUtil.desaturate(AllIcons.Actions.Suspend))
  private val commandsPanel: CommandsTabPanel =
      CommandsTabPanel(project) { cmdId: CommandId ->
        ApplicationManager.getApplication().invokeLater {
          sendMessage(project, cmdId.displayName, cmdId)
        }
      }
  val contextView: EnhancedContextPanel
  var isChatVisible = false
  var chatId: String? = null
  private var codyOnboardingGuidancePanel: CodyOnboardingGuidancePanel? = null
  private val chatMessageHistory = CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY)
  private val historyTree = HistoryTree(::selectChat, ::deleteChat)

  init {
    // Tabs
    val contentPanel = JPanel()
    tabbedPane.insertSimpleTab("Chat", contentPanel, CHAT_TAB_INDEX)
    tabbedPane.insertSimpleTab("Commands", commandsPanel, RECIPES_TAB_INDEX)
    subscriptionPanel = SubscriptionTabPanel()
    tabbedPane.insertSimpleTab("Commands", commandsPanel, RECIPES_TAB_INDEX)
    tabbedPane.insertSimpleTab("Chat History", historyTree, HISTORY_TAB_INDEX)

    // Chat panel
    messagesPanel.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true)
    val chatPanel = ChatScrollPane(messagesPanel)

    // Controls panel
    sendButton = createSendButton()
    promptPanel =
        PromptPanel(
            chatMessageHistory,
            ::sendChatMessage,
            sendButton,
            isGenerating = stopGeneratingButton::isVisible)
    val stopGeneratingButtonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 5))
    stopGeneratingButtonPanel.minimumSize = Dimension(Short.MAX_VALUE.toInt(), 0)
    stopGeneratingButton.addActionListener {
      inProgressChat.abort()
      stopGeneratingButton.isVisible = false
      sendButton.isEnabled = promptPanel.textArea.text.isNotBlank()
      ensureBlinkingCursorIsNotDisplayed()
      commandsPanel.enableAllButtons()
    }

    stopGeneratingButton.isVisible = false
    stopGeneratingButtonPanel.add(stopGeneratingButton)
    stopGeneratingButtonPanel.isOpaque = false
    contextView = EnhancedContextPanel(project)
    val lowerPanel = LowerPanel(stopGeneratingButtonPanel, promptPanel, contextView)

    // Main content panel
    contentPanel.layout = BorderLayout(0, 0)
    contentPanel.border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
    contentPanel.add(chatPanel, BorderLayout.CENTER)
    contentPanel.add(lowerPanel, BorderLayout.SOUTH)
    tabbedPane.addChangeListener { focusPromptInput() }
    val singInWithSourcegraphPanel = SignInWithSourcegraphPanel(project)
    allContentPanel.add(tabbedPane, "tabbedPane", CHAT_PANEL_INDEX)
    allContentPanel.add(
        singInWithSourcegraphPanel, SING_IN_WITH_SOURCEGRAPH_PANEL, SIGN_IN_PANEL_INDEX)
    allContentLayout.show(allContentPanel, SING_IN_WITH_SOURCEGRAPH_PANEL)
    refreshPanelsVisibility()

    ApplicationManager.getApplication().executeOnPooledThread { refreshSubscriptionTab() }
    refreshChatToEmpty()
    historyTree.refreshTree()
  }

  @RequiresBackgroundThread
  fun refreshSubscriptionTab() {
    CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
      fetchSubscriptionPanelData(project, agent.server).thenAccept {
        if (it != null) {

          ApplicationManager.getApplication().invokeLater { refreshSubscriptionTab(it) }
        }
      }
    }
  }

  @RequiresEdt
  private fun refreshSubscriptionTab(data: SubscriptionTabPanelData) {
    val isSubscriptionTabPresent = tabbedPane.tabCount >= SUBSCRIPTION_TAB_INDEX + 1

    if (data.isDotcomAccount && data.codyProFeatureFlag) {
      if (!isSubscriptionTabPresent) {
        tabbedPane.insertTab(
            /* title = */ "Subscription",
            /* icon = */ null,
            /* component = */ subscriptionPanel,
            /* tip = */ null,
            SUBSCRIPTION_TAB_INDEX)
      }
      subscriptionPanel.update(data.isCurrentUserPro)
    } else if (isSubscriptionTabPresent) {
      tabbedPane.remove(SUBSCRIPTION_TAB_INDEX)
    }
  }

  fun refreshChatToEmpty() {
    CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
      ApplicationManager.getApplication().invokeLater {
        messagesPanel.removeAll()
        chatMessageHistory.clearHistory()
        inProgressChat.abort()
        addWelcomeMessage()
        activateChatTab()
      }
      chatId = agent.server.chatNew().get()
    }
  }

  private fun selectChat(item: ChatState) {
    ApplicationManager.getApplication().invokeLater {
      messagesPanel.removeAll()
      addWelcomeMessage()
      val chat = HistoryService.getInstance().getChatByPanelId(item.panelId!!)
      for (message in chat.messages) {
        displayUsedContext(
            message.contextFiles.map {
              ContextMessage(Speaker.ASSISTANT, "", ContextFile(URI.create(it)))
            })
        addChatMessageAsComponent(ChatMessage(message.speaker!!, message.text, message.text))
      }
      CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
        val model = agent.server.chatModels(ChatModelsParams(chatId!!)).get().models.first().model
        val chatMessages = chat.messages.map { ChatMessage(it.speaker!!, it.text, it.text) }
        if (item.replyChatId != null) {
          val restoredId =
              agent.server
                  .chatRestore(ChatRestoreParams(model, chatMessages, item.replyChatId!!))
                  .get()
          chat.panelId = restoredId
          chatId = restoredId
        }
      }
      activateChatTab()
    }
    // todo add to test plan: when cody_history.xml can't be deserialized by any cause, this will
    // freeze CodyToolWindowContent totally - IMO this is long term issue
  }

  private fun deleteChat(item: ChatState) {
    HistoryService.getInstance().removeChat(item.panelId!!)
    if (chatId == item.panelId) {
      refreshChatToEmpty()
    }
    historyTree.refreshTree()
  }

  private fun addChatMessageAsComponent(message: ChatMessage) {
    addComponentToChat(
        MessagePanel(
            message, project, messagesPanel, ChatUIConstants.ASSISTANT_MESSAGE_GRADIENT_WIDTH))
  }

  @RequiresEdt
  fun refreshPanelsVisibility() {
    val codyAuthenticationManager = CodyAuthenticationManager.instance
    if (codyAuthenticationManager.getAccounts().isEmpty()) {
      allContentLayout.show(allContentPanel, SING_IN_WITH_SOURCEGRAPH_PANEL)
      isChatVisible = false
      return
    }
    val activeAccount = codyAuthenticationManager.getActiveAccount(project)
    if (!CodyApplicationSettings.instance.isOnboardingGuidanceDismissed) {
      val displayName = activeAccount?.let(CodyAccount::displayName)
      val newCodyOnboardingGuidancePanel = CodyOnboardingGuidancePanel(displayName)
      newCodyOnboardingGuidancePanel.addMainButtonActionListener {
        CodyApplicationSettings.instance.isOnboardingGuidanceDismissed = true
        refreshPanelsVisibility()
      }
      if (displayName != null) {
        if (codyOnboardingGuidancePanel?.originalDisplayName?.let { it != displayName } == true)
            try {
              allContentPanel.remove(ONBOARDING_PANEL_INDEX)
            } catch (ex: Throwable) {
              // ignore because panel was not created before
            }
      }
      codyOnboardingGuidancePanel = newCodyOnboardingGuidancePanel
      allContentPanel.add(codyOnboardingGuidancePanel, ONBOARDING_PANEL, ONBOARDING_PANEL_INDEX)
      allContentLayout.show(allContentPanel, ONBOARDING_PANEL)
      isChatVisible = false
      return
    }
    allContentLayout.show(allContentPanel, "tabbedPane")
    isChatVisible = true
  }

  private fun addWelcomeMessage() {
    val welcomeText =
        "Hello! I'm Cody. I can write code and answer questions for you. See [Cody documentation](https://sourcegraph.com/docs/cody) for help and tips."
    addChatMessageAsComponent(ChatMessage(Speaker.ASSISTANT, welcomeText))
  }

  private fun createSendButton(): JButton {
    val myButton = SendButton()

    myButton.addActionListener { _: ActionEvent? ->
      GraphQlLogger.logCodyEvent(this.project, "recipe:chat-question", "clicked")
      sendChatMessage()
    }

    return myButton
  }

  @Synchronized
  fun addMessageToChat(message: ChatMessage, shouldDisplayBlinkingCursor: Boolean = false) {
    ApplicationManager.getApplication().invokeLater {
      addChatMessageAsComponent(message)
      HistoryService.getInstance().addMessage(chatId!!, message)
      historyTree.refreshTree()
      ensureBlinkingCursorIsNotDisplayed()
      if (shouldDisplayBlinkingCursor) {
        messagesPanel.add(BlinkingCursorComponent.instance)
        BlinkingCursorComponent.instance.timer.start()
      }
    }
  }

  private fun addComponentToChat(messageContent: JPanel) {
    val wrapperPanel = JPanel()
    wrapperPanel.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    // Chat message
    wrapperPanel.add(messageContent, VerticalFlowLayout.TOP)
    messagesPanel.add(wrapperPanel)
    messagesPanel.revalidate()
    messagesPanel.repaint()
  }

  private fun activateChatTab() {
    tabbedPane.selectedIndex = CHAT_TAB_INDEX
  }

  @Synchronized
  fun updateLastMessage(message: ChatMessage) {
    ApplicationManager.getApplication().invokeLater {
      if (messagesPanel.componentCount > 0) {
        val lastPanel = messagesPanel.components.last() as? JPanel
        val lastMessage = lastPanel?.getComponent(0) as? MessagePanel
        lastMessage?.updateContentWith(message)
        HistoryService.getInstance().updateMessage(chatId!!, message)
      }
    }
  }

  private fun startMessageProcessing() {
    inProgressChat = CancellationToken()
    ApplicationManager.getApplication().invokeLater {
      stopGeneratingButton.isVisible = true
      sendButton.isEnabled = false
      commandsPanel.disableAllButtons()
    }
  }

  fun finishMessageProcessing() {
    ApplicationManager.getApplication().invokeLater {
      ensureBlinkingCursorIsNotDisplayed()
      stopGeneratingButton.isVisible = false
      sendButton.isEnabled = promptPanel.textArea.text.isNotBlank()
      commandsPanel.enableAllButtons()
    }
  }

  private fun ensureBlinkingCursorIsNotDisplayed() {
    Arrays.stream(messagesPanel.components)
        .filter { x: Component -> x === BlinkingCursorComponent.instance }
        .forEach { messagesPanel.remove(BlinkingCursorComponent.instance) }
    BlinkingCursorComponent.instance.timer.stop()
  }

  @RequiresEdt
  private fun sendChatMessage() {
    val text = promptPanel.textArea.text
    chatMessageHistory.messageSent(text)
    sendMessage(project, text, commandId = null)
    promptPanel.reset()
  }

  @RequiresEdt
  private fun sendMessage(project: Project, message: String?, commandId: CommandId?) {
    startMessageProcessing()
    val displayText = XmlStringUtil.escapeString(message)
    val humanMessage = ChatMessage(Speaker.HUMAN, message, displayText)
    addMessageToChat(humanMessage, shouldDisplayBlinkingCursor = true)
    activateChatTab()

    // This cannot run on EDT (Event Dispatch Thread) because it may block for a long time.
    // Also, if we did the back-end call in the main thread and then waited, we wouldn't see the
    // messages streamed back to us.
    ApplicationManager.getApplication().executeOnPooledThread {
      val chat = Chat()
      try {
        chat.sendMessageViaAgent(
            project,
            humanMessage,
            commandId,
            this,
            inProgressChat,
            contextView.isEnhancedContextEnabled.get())
      } catch (e: Exception) {
        logger.error("Error sending message '$humanMessage' to chat", e)
        addMessageToChat(
            ChatMessage(
                Speaker.ASSISTANT,
                "Cody is not able to reply at the moment. " +
                    "This is a bug, please report an issue to https://github.com/sourcegraph/cody/issues/new?template=bug_report.yml " +
                    "and include as many details as possible to help troubleshoot the problem."))
        finishMessageProcessing()
      }
    }
    GraphQlLogger.logCodyEvent(this.project, "command:chat-question", "executed")
  }

  fun displayUsedContext(contextMessages: List<ContextMessage>) {
    if (contextMessages.isEmpty()) {
      // Do nothing when there are no context files. It's normal that some answers have no context
      // files.
      return
    }
    val contextFilesMessage = ContextFilesMessage(project, contextMessages)
    val messageContentPanel = JPanel(BorderLayout())
    messageContentPanel.add(contextFilesMessage)
    addComponentToChat(messageContentPanel)
  }

  private fun focusPromptInput() {
    if (tabbedPane.selectedIndex == CHAT_TAB_INDEX) {
      promptPanel.textArea.requestFocusInWindow()
      val textLength = promptPanel.textArea.document.length
      promptPanel.textArea.caretPosition = textLength
    }
  }

  val preferredFocusableComponent: JComponent?
    get() = if (tabbedPane.selectedIndex == CHAT_TAB_INDEX) promptPanel.textArea else null

  fun addToTabbedPaneChangeListener(myAction: () -> Unit) =
      tabbedPane.addChangeListener { myAction() }

  companion object {
    const val ONBOARDING_PANEL = "onboardingPanel"
    const val CHAT_PANEL_INDEX = 0
    const val SIGN_IN_PANEL_INDEX = 1
    const val ONBOARDING_PANEL_INDEX = 2
    var logger = Logger.getInstance(CodyToolWindowContent::class.java)
    const val SING_IN_WITH_SOURCEGRAPH_PANEL = "singInWithSourcegraphPanel"
    private const val CHAT_TAB_INDEX = 0
    private const val RECIPES_TAB_INDEX = 1
    private const val HISTORY_TAB_INDEX = 2 // todo chat history should be after chat
    private const val SUBSCRIPTION_TAB_INDEX = 3
    private const val CHAT_MESSAGE_HISTORY_CAPACITY = 100

    fun executeOnInstanceIfNotDisposed(
        project: Project,
        myAction: CodyToolWindowContent.() -> Unit
    ) {
      if (!project.isDisposed) {
        val codyToolWindowContent = project.getService(CodyToolWindowContent::class.java)
        codyToolWindowContent.myAction()
      }
    }

    private fun JBTabbedPane.insertSimpleTab(title: String, component: Component, index: Int) =
        insertTab(title, /* icon = */ null, component, /* tip = */ null, index)
  }
}
