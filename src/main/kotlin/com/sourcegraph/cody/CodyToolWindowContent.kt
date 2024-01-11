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
import com.sourcegraph.cody.agent.CodyAgent.Companion.getInitializedServer
import com.sourcegraph.cody.agent.CodyAgent.Companion.isConnected
import com.sourcegraph.cody.agent.CodyAgentManager.tryRestartingAgentIfNotRunning
import com.sourcegraph.cody.agent.protocol.*
import com.sourcegraph.cody.chat.*
import com.sourcegraph.cody.config.CodyAccount
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.context.EmbeddingStatusView
import com.sourcegraph.cody.ui.ChatScrollPane
import com.sourcegraph.cody.ui.SendButton
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.telemetry.GraphQlLogger
import java.awt.*
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.*

@Service(Service.Level.PROJECT)
class CodyToolWindowContent(private val project: Project) : UpdatableChat {
  private val allContentLayout = CardLayout()
  private val allContentPanel = JPanel(allContentLayout)
  private val tabbedPane = JBTabbedPane()
  private val messagesPanel = JPanel()
  private val promptPanel: PromptPanel
  private val sendButton: JButton
  private var inProgressChat = CancellationToken()
  private val stopGeneratingButton =
      JButton("Stop generating", IconUtil.desaturate(AllIcons.Actions.Suspend))
  private val recipesPanel: RecipesPanel
  private val subscriptionPanel: SubscriptionTabPanel
  val embeddingStatusView = EmbeddingStatusView(project)
  override var isChatVisible = false
  override var id: String? = null
  private var codyOnboardingGuidancePanel: CodyOnboardingGuidancePanel? = null
  private val chatMessageHistory = CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY)

  init {
    // Tabs
    val contentPanel = JPanel()
    tabbedPane.insertTab("Chat", null, contentPanel, null, CHAT_TAB_INDEX)
    recipesPanel = RecipesPanel(project, ::sendMessage)
    tabbedPane.insertTab("Commands", null, recipesPanel, null, RECIPES_TAB_INDEX)
    subscriptionPanel = SubscriptionTabPanel()

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
    stopGeneratingButtonPanel.preferredSize =
        Dimension(Short.MAX_VALUE.toInt(), stopGeneratingButton.getPreferredSize().height + 10)
    stopGeneratingButton.addActionListener {
      inProgressChat.abort()
      stopGeneratingButton.isVisible = false
      sendButton.isEnabled = promptPanel.textArea.text.isNotBlank()
      ensureBlinkingCursorIsNotDisplayed()
      recipesPanel.enableRecipes()
    }
    stopGeneratingButton.isVisible = false
    stopGeneratingButtonPanel.add(stopGeneratingButton)
    stopGeneratingButtonPanel.isOpaque = false
    val lowerPanel = LowerPanel(stopGeneratingButtonPanel, promptPanel, embeddingStatusView)

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

    addWelcomeMessage()

    recipesPanel.refreshAndFetch()

    ApplicationManager.getApplication().executeOnPooledThread {
      refreshSubscriptionTab()
      loadNewChatId()
    }
  }

  @RequiresBackgroundThread
  fun refreshSubscriptionTab() {
    fetchSubscriptionPanelData(project).thenAccept {
      if (it != null) {
        ApplicationManager.getApplication().invokeLater { refreshSubscriptionTab(it) }
      }
    }
  }

  @RequiresEdt
  private fun refreshSubscriptionTab(data: SubscriptionTabPanelData) {
    if (data.isDotcomAccount && data.codyProFeatureFlag) {
      if (tabbedPane.tabCount < SUBSCRIPTION_TAB_INDEX + 1) {
        tabbedPane.insertTab("Subscription", null, subscriptionPanel, null, SUBSCRIPTION_TAB_INDEX)
      }
      subscriptionPanel.update(data.isCurrentUserPro)
    } else {
      tabbedPane.remove(SUBSCRIPTION_TAB_INDEX)
    }
  }

  @RequiresBackgroundThread
  override fun loadNewChatId(callback: () -> Unit) {
    id = null

    ApplicationManager.getApplication().invokeLater {
      promptPanel.textArea.isEnabled = false
      promptPanel.textArea.emptyText.text = "Connecting to agent..."
    }

    getInitializedServer(project).thenAccept { server ->
      id = server.chatNew().get()
      ApplicationManager.getApplication().invokeLater {
        promptPanel.textArea.isEnabled = true
        promptPanel.textArea.emptyText.text = "Ask a question about this code..."
      }
      callback.invoke()
    }
  }

  @RequiresEdt
  override fun refreshPanelsVisibility() {
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
        recipesPanel.refreshAndFetch()
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

  @RequiresEdt
  private fun addWelcomeMessage() {
    val welcomeText =
        "Hello! I'm Cody. I can write code and answer questions for you. See [Cody documentation](https://sourcegraph.com/docs/cody) for help and tips."
    addMessageToChat(ChatMessage(Speaker.ASSISTANT, welcomeText))
  }

  @RequiresEdt
  private fun createSendButton(): JButton {
    val myButton = SendButton()

    myButton.addActionListener { _: ActionEvent? ->
      GraphQlLogger.logCodyEvent(this.project, "recipe:chat-question", "clicked")
      sendChatMessage()
    }

    return myButton
  }

  @Synchronized
  @RequiresEdt
  override fun addMessageToChat(message: ChatMessage, shouldDisplayBlinkingCursor: Boolean) {
    // Bubble panel
    val messagePanel =
        MessagePanel(
            message, project, messagesPanel, ChatUIConstants.ASSISTANT_MESSAGE_GRADIENT_WIDTH)
    addComponentToChat(messagePanel)
    ensureBlinkingCursorIsNotDisplayed()
    if (shouldDisplayBlinkingCursor) {
      messagesPanel.add(BlinkingCursorComponent.instance)
      BlinkingCursorComponent.instance.timer.start()
    }
  }

  @RequiresEdt
  private fun addComponentToChat(messageContent: JPanel) {
    val wrapperPanel = JPanel()
    wrapperPanel.layout = VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)
    // Chat message
    wrapperPanel.add(messageContent, VerticalFlowLayout.TOP)
    messagesPanel.add(wrapperPanel)
    messagesPanel.revalidate()
    messagesPanel.repaint()
  }

  @RequiresEdt
  override fun activateChatTab() {
    tabbedPane.selectedIndex = CHAT_TAB_INDEX
  }

  @Synchronized
  @RequiresEdt
  override fun updateLastMessage(message: ChatMessage) {
    Optional.of(messagesPanel)
        .filter { mp: JPanel -> mp.componentCount > 0 }
        .map { mp: JPanel -> mp.getComponent(mp.componentCount - 1) }
        .filter { component: Component? -> component is JPanel }
        .map { component: Component -> component as JPanel }
        .map { lastWrapperPanel: JPanel -> lastWrapperPanel.getComponent(0) }
        .filter { component: Component? -> component is MessagePanel }
        .map { component: Component -> component as MessagePanel }
        .ifPresent { lastMessage: MessagePanel -> lastMessage.updateContentWith(message) }
  }

  @RequiresEdt
  private fun startMessageProcessing() {
    inProgressChat = CancellationToken()
    stopGeneratingButton.isVisible = true
    sendButton.isEnabled = false
    recipesPanel.disableRecipes()
  }

  @RequiresEdt
  override fun finishMessageProcessing() {
    ensureBlinkingCursorIsNotDisplayed()
    stopGeneratingButton.isVisible = false
    sendButton.isEnabled = promptPanel.textArea.text.isNotBlank()
    recipesPanel.enableRecipes()
  }

  @RequiresEdt
  override fun resetConversation() {
    stopGeneratingButton.isVisible = false
    messagesPanel.removeAll()
    addWelcomeMessage()
    messagesPanel.revalidate()
    messagesPanel.repaint()
    chatMessageHistory.clearHistory()
    // todo (#260): call agent to reset the transcript instead of unsetting the chat id
    inProgressChat.abort()
    ensureBlinkingCursorIsNotDisplayed()
    ApplicationManager.getApplication().executeOnPooledThread { loadNewChatId() }
  }

  @RequiresEdt
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
    sendMessage(text, "chat-question")
    promptPanel.reset()
  }

  @RequiresEdt
  private fun sendMessage(message: String, recipeId: String) {
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
      tryRestartingAgentIfNotRunning(project)
      if (isConnected(project)) {
        try {
          chat.sendMessageViaAgent(project, humanMessage, recipeId, this, inProgressChat)
        } catch (e: Exception) {
          logger.warn("Error sending message '$humanMessage' to chat", e)
        }
      } else {
        logger.warn("Agent is disabled, can't use chat.")
        ApplicationManager.getApplication().invokeLater {
          addMessageToChat(
              ChatMessage(
                  Speaker.ASSISTANT,
                  "Cody is not able to reply at the moment. " +
                      "This is a bug, please report an issue to https://github.com/sourcegraph/cody/issues/new?template=bug_report.yml " +
                      "and include as many details as possible to help troubleshoot the problem."))
          finishMessageProcessing()
        }
      }
      GraphQlLogger.logCodyEvent(this.project, "recipe:chat-question", "executed")
    }
  }

  @RequiresEdt
  override fun displayUsedContext(contextMessages: List<ContextMessage?>) {
    if (contextMessages.isEmpty()) {
      // Do nothing when there are no context files. It's normal that some answers have no context
      // files.
      return
    }
    val contextFilesMessage = ContextFilesMessage(contextMessages)
    val messageContentPanel = JPanel(BorderLayout())
    messageContentPanel.add(contextFilesMessage)
    addComponentToChat(messageContentPanel)
  }

  val contentPanel: JComponent
    get() = allContentPanel

  @RequiresEdt
  private fun focusPromptInput() {
    if (tabbedPane.selectedIndex == CHAT_TAB_INDEX && promptPanel.textArea.isEnabled) {
      promptPanel.textArea.requestFocusInWindow()
      val textLength = promptPanel.textArea.document.length
      promptPanel.textArea.caretPosition = textLength
    }
  }

  val preferredFocusableComponent: JComponent?
    get() = if (tabbedPane.selectedIndex == CHAT_TAB_INDEX) promptPanel.textArea else null

  @RequiresEdt
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
    private const val SUBSCRIPTION_TAB_INDEX = 2
    private const val CHAT_MESSAGE_HISTORY_CAPACITY = 100

    fun getInstance(project: Project): CodyToolWindowContent {
      return project.getService(CodyToolWindowContent::class.java)
    }
  }
}
