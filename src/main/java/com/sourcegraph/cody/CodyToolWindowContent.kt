package com.sourcegraph.cody

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ColorUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import com.sourcegraph.cody.agent.CodyAgent.Companion.getClient
import com.sourcegraph.cody.agent.CodyAgent.Companion.getInitializedServer
import com.sourcegraph.cody.agent.CodyAgent.Companion.getServer
import com.sourcegraph.cody.agent.CodyAgent.Companion.isConnected
import com.sourcegraph.cody.agent.CodyAgentManager.tryRestartingAgentIfNotRunning
import com.sourcegraph.cody.agent.protocol.RecipeInfo
import com.sourcegraph.cody.api.Speaker
import com.sourcegraph.cody.chat.*
import com.sourcegraph.cody.config.CodyAccount
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.context.ContextMessage
import com.sourcegraph.cody.context.EmbeddingStatusView
import com.sourcegraph.cody.ui.AutoGrowingTextArea
import com.sourcegraph.cody.ui.ChatScrollPane
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.telemetry.GraphQlLogger
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.*
import java.util.stream.Collectors
import javax.swing.*
import javax.swing.KeyStroke.*
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.event.ChangeEvent
import javax.swing.event.DocumentEvent
import javax.swing.plaf.ButtonUI
import javax.swing.text.DefaultEditorKit

@Service(Service.Level.PROJECT)
class CodyToolWindowContent(private val project: Project) : UpdatableChat {
  private val allContentLayout = CardLayout()
  private val allContentPanel = JPanel(allContentLayout)
  private val tabbedPane = JBTabbedPane()
  private val messagesPanel = JPanel()
  private val promptInput: JBTextArea
  private val sendButton: JButton
  private var inProgressChat = CancellationToken()
  private val stopGeneratingButton =
      JButton("Stop generating", IconUtil.desaturate(AllIcons.Actions.Suspend))
  private val recipesPanel: JBPanelWithEmptyText
  val embeddingStatusView: EmbeddingStatusView
  override var isChatVisible = false
  private var codyOnboardingGuidancePanel: CodyOnboardingGuidancePanel? = null
  private val chatMessageHistory = CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY)
  private var isInHistoryMode = true

  init {
    // Tabs
    val contentPanel = JPanel()
    tabbedPane.insertTab("Chat", null, contentPanel, null, CHAT_TAB_INDEX)
    recipesPanel = JBPanelWithEmptyText(GridLayout(0, 1))
    recipesPanel.setLayout(BoxLayout(recipesPanel, BoxLayout.Y_AXIS))
    tabbedPane.insertTab("Commands", null, recipesPanel, null, RECIPES_TAB_INDEX)

    // Initiate filling recipes panel in the background
    refreshRecipes()

    // Chat panel
    messagesPanel.setLayout(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true))
    val chatPanel = ChatScrollPane(messagesPanel)

    // Controls panel
    val controlsPanel = JPanel()
    controlsPanel.setLayout(BorderLayout())
    controlsPanel.setBorder(EmptyBorder(JBUI.insets(0, 14, 14, 14)))
    val promptPanel = JPanel(BorderLayout())
    sendButton = createSendButton(project)
    val autoGrowingTextArea = AutoGrowingTextArea(3, 9, promptPanel)
    promptInput = autoGrowingTextArea.textArea
    /* Submit on enter */
    val JUST_ENTER = KeyboardShortcut(getKeyStroke(KeyEvent.VK_ENTER, 0), null)
    val UP = KeyboardShortcut(getKeyStroke(KeyEvent.VK_UP, 0), null)
    val DOWN = KeyboardShortcut(getKeyStroke(KeyEvent.VK_DOWN, 0), null)
    val DEFAULT_SUBMIT_ACTION_SHORTCUT: ShortcutSet = CustomShortcutSet(JUST_ENTER)
    val POP_UPPER_MESSAGE_ACTION_SHORTCUT: ShortcutSet = CustomShortcutSet(UP)
    val POP_LOWER_MESSAGE_ACTION_SHORTCUT: ShortcutSet = CustomShortcutSet(DOWN)
    val upperMessageAction: AnAction =
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            if (isInHistoryMode) {
              chatMessageHistory.popUpperMessage(promptInput)
            } else {
              val defaultAction = promptInput.actionMap[DefaultEditorKit.upAction]
              defaultAction.actionPerformed(null)
            }
          }
        }
    val lowerMessageAction: AnAction =
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            if (isInHistoryMode) {
              chatMessageHistory.popLowerMessage(promptInput)
            } else {
              val defaultAction = promptInput.actionMap[DefaultEditorKit.downAction]
              defaultAction.actionPerformed(null)
            }
          }
        }
    val sendMessageAction: AnAction =
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            if (promptInput.getText().isNotEmpty()) {
              sendChatMessage(project)
            }
          }
        }
    sendMessageAction.registerCustomShortcutSet(DEFAULT_SUBMIT_ACTION_SHORTCUT, promptInput)
    upperMessageAction.registerCustomShortcutSet(POP_UPPER_MESSAGE_ACTION_SHORTCUT, promptInput)
    lowerMessageAction.registerCustomShortcutSet(POP_LOWER_MESSAGE_ACTION_SHORTCUT, promptInput)
    promptInput.addKeyListener(
        object : KeyAdapter() {
          override fun keyReleased(e: KeyEvent) {
            val keyCode = e.keyCode
            if (keyCode != KeyEvent.VK_UP && keyCode != KeyEvent.VK_DOWN) {
              isInHistoryMode = promptInput.getText().isEmpty()
            }
          }
        })
    // Enable/disable the send button based on whether promptInput is empty
    promptInput.document.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            sendButton.setEnabled(promptInput.getText().isNotEmpty())
          }
        })
    promptPanel.add(autoGrowingTextArea.scrollPane, BorderLayout.CENTER)
    promptPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0))
    val stopGeneratingButtonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 5))
    stopGeneratingButtonPanel.preferredSize =
        Dimension(Short.MAX_VALUE.toInt(), stopGeneratingButton.getPreferredSize().height + 10)
    stopGeneratingButton.addActionListener { e: ActionEvent? ->
      inProgressChat.abort()
      stopGeneratingButton.isVisible = false
      sendButton.setEnabled(true)
    }
    stopGeneratingButton.isVisible = false
    stopGeneratingButtonPanel.add(stopGeneratingButton)
    stopGeneratingButtonPanel.setOpaque(false)
    controlsPanel.add(promptPanel, BorderLayout.NORTH)
    controlsPanel.add(sendButton, BorderLayout.EAST)
    val lowerPanel = JPanel(BorderLayout())
    val borderColor = ColorUtil.brighter(UIUtil.getPanelBackground(), 3)
    val topBorder: Border = BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor)
    lowerPanel.setBorder(topBorder)
    lowerPanel.setLayout(BoxLayout(lowerPanel, BoxLayout.Y_AXIS))
    lowerPanel.add(stopGeneratingButtonPanel)
    lowerPanel.add(controlsPanel)
    embeddingStatusView = EmbeddingStatusView(project)
    embeddingStatusView.setBorder(topBorder)
    lowerPanel.add(embeddingStatusView)

    // Main content panel
    contentPanel.setLayout(BorderLayout(0, 0))
    contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0))
    contentPanel.add(chatPanel, BorderLayout.CENTER)
    contentPanel.add(lowerPanel, BorderLayout.SOUTH)
    tabbedPane.addChangeListener { e: ChangeEvent? -> focusPromptInput() }
    val singInWithSourcegraphPanel = SignInWithSourcegraphPanel(project)
    allContentPanel.add(tabbedPane, "tabbedPane", CHAT_PANEL_INDEX)
    allContentPanel.add(
        singInWithSourcegraphPanel, SING_IN_WITH_SOURCEGRAPH_PANEL, SIGN_IN_PANEL_INDEX)
    allContentLayout.show(allContentPanel, SING_IN_WITH_SOURCEGRAPH_PANEL)
    updateVisibilityOfContentPanels()
    // Add welcome message
    addWelcomeMessage()
  }

  @RequiresEdt
  fun refreshRecipes() {
    recipesPanel.removeAll()
    recipesPanel.emptyText.setText("Loading recipes...")
    recipesPanel.revalidate()
    recipesPanel.repaint()
    val server = getServer(project)
    if (server == null) {
      setRecipesPanelError()
      return
    }
    ApplicationManager.getApplication().executeOnPooledThread { // Non-blocking data fetch
      try {
        server.recipesList().thenAccept { recipes: List<RecipeInfo> ->
          ApplicationManager.getApplication().invokeLater { updateUIWithRecipeList(recipes) }
        } // Update on EDT
      } catch (e: Exception) {
        logger.warn("Error fetching recipes from agent", e)
        // Update on EDT
        ApplicationManager.getApplication().invokeLater { setRecipesPanelError() }
      }
    }
  }

  @RequiresEdt
  private fun setRecipesPanelError() {
    val emptyText = recipesPanel.emptyText
    emptyText.setText(
        "Error fetching recipes. Check your connection. If the problem persists, please contact support.")
    emptyText.appendLine(
        "Retry",
        SimpleTextAttributes(
            SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.Foreground.ENABLED)) {
          refreshRecipes()
        }
  }

  @RequiresEdt
  private fun updateUIWithRecipeList(recipes: List<RecipeInfo>) {
    // we don't want to display recipes with ID "chat-question" and "code-question"
    val excludedRecipeIds: List<String?> =
        listOf("chat-question", "code-question", "translate-to-language")
    val recipesToDisplay =
        recipes
            .stream()
            .filter { recipe: RecipeInfo -> !excludedRecipeIds.contains(recipe.id) }
            .collect(Collectors.toList())
    fillRecipesPanel(recipesToDisplay)
    fillContextMenu(recipesToDisplay)
  }

  @RequiresEdt
  private fun fillRecipesPanel(recipes: List<RecipeInfo>) {
    recipesPanel.removeAll()

    // Loop on recipes and add a button for each item
    for (recipe in recipes) {
      if (recipe.id == null || recipe.title == null) {
        continue
      }
      val recipeButton = createRecipeButton(recipe.title!!)
      recipeButton.addActionListener { e: ActionEvent? ->
        GraphQlLogger.logCodyEvent(project, "recipe:" + recipe.id, "clicked")
        sendMessage(project, recipe.title!!, recipe.id!!)
      }
      recipesPanel.add(recipeButton)
    }
  }

  private fun fillContextMenu(recipes: List<RecipeInfo>) {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("CodyEditorActions") as DefaultActionGroup

    // Loop on recipes and create an action for each new item
    for (recipe in recipes) {
      if (recipe.id == null || recipe.title == null) {
        continue
      }
      val actionId = "cody.recipe." + recipe.id
      val existingAction = actionManager.getAction(actionId)
      if (existingAction != null) {
        continue
      }
      val action: DumbAwareAction =
          object : DumbAwareAction(recipe.title) {
            override fun actionPerformed(e: AnActionEvent) {
              GraphQlLogger.logCodyEvent(project, "recipe:" + recipe.id, "clicked")
              sendMessage(project, recipe.title!!, recipe.id!!)
            }
          }
      actionManager.registerAction(actionId, action)
      group.addAction(action)
    }
  }

  @RequiresEdt
  private fun updateVisibilityOfContentPanels() {
    val codyAuthenticationManager = CodyAuthenticationManager.instance
    if (codyAuthenticationManager.getAccounts().isEmpty()) {
      allContentLayout.show(allContentPanel, SING_IN_WITH_SOURCEGRAPH_PANEL)
      isChatVisible = false
      return
    }
    val activeAccount = codyAuthenticationManager.getActiveAccount(project)
    if (!CodyApplicationSettings.instance.isOnboardingGuidanceDismissed) {
      val displayName =
          Optional.ofNullable(activeAccount).map(CodyAccount::displayName).orElse(null)
      val newCodyOnboardingGuidancePanel = CodyOnboardingGuidancePanel(displayName)
      newCodyOnboardingGuidancePanel.addMainButtonActionListener {
        CodyApplicationSettings.instance.isOnboardingGuidanceDismissed = true
        updateVisibilityOfContentPanels()
        refreshRecipes()
      }
      if (displayName != null) {
        if (codyOnboardingGuidancePanel != null &&
            displayName != codyOnboardingGuidancePanel!!.originalDisplayName)
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

  private fun createRecipeButton(text: String): JButton {
    val button = JButton(text)
    button.setAlignmentX(Component.CENTER_ALIGNMENT)
    button.maximumSize = Dimension(Int.MAX_VALUE, button.getPreferredSize().height)
    val buttonUI = DarculaButtonUI.createUI(button) as ButtonUI
    button.setUI(buttonUI)
    return button
  }

  private fun addWelcomeMessage() {
    val welcomeText =
        "Hello! I'm Cody. I can write code and answer questions for you. See [Cody documentation](https://docs.sourcegraph.com/cody) for help and tips."
    addMessageToChat(ChatMessage(Speaker.ASSISTANT, welcomeText))
  }

  private fun createSendButton(project: Project): JButton {
    val sendButton = JButton("Send")
    sendButton.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, java.lang.Boolean.TRUE)
    val buttonUI = DarculaButtonUI.createUI(sendButton) as ButtonUI
    sendButton.setUI(buttonUI)
    sendButton.addActionListener { e: ActionEvent? ->
      GraphQlLogger.logCodyEvent(this.project, "recipe:chat-question", "clicked")
      sendChatMessage(project)
    }
    return sendButton
  }

  @Synchronized
  override fun addMessageToChat(message: ChatMessage, shouldDisplayBlinkingCursor: Boolean) {
    ApplicationManager.getApplication().invokeLater {

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
  }

  fun addComponentToChat(messageContent: JPanel) {
    val wrapperPanel = JPanel()
    wrapperPanel.setLayout(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false))
    // Chat message
    wrapperPanel.add(messageContent, VerticalFlowLayout.TOP)
    messagesPanel.add(wrapperPanel)
    messagesPanel.revalidate()
    messagesPanel.repaint()
  }

  override fun activateChatTab() {
    tabbedPane.setSelectedIndex(CHAT_TAB_INDEX)
  }

  @Synchronized
  override fun updateLastMessage(message: ChatMessage) {
    ApplicationManager.getApplication().invokeLater {
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
  }

  private fun startMessageProcessing() {
    inProgressChat = CancellationToken()
    ApplicationManager.getApplication().invokeLater {
      stopGeneratingButton.isVisible = true
      sendButton.setEnabled(false)
    }
  }

  override fun finishMessageProcessing() {
    ApplicationManager.getApplication().invokeLater {
      stopGeneratingButton.isVisible = false
      sendButton.setEnabled(true)
      ensureBlinkingCursorIsNotDisplayed()
    }
  }

  override fun resetConversation() {
    ApplicationManager.getApplication().invokeLater {
      stopGeneratingButton.isVisible = false
      sendButton.setEnabled(true)
      messagesPanel.removeAll()
      addWelcomeMessage()
      messagesPanel.revalidate()
      messagesPanel.repaint()
      chatMessageHistory.clearHistory()
      getInitializedServer(project).thenAccept { it?.transcriptReset() }
      ensureBlinkingCursorIsNotDisplayed()
    }
  }

  private fun ensureBlinkingCursorIsNotDisplayed() {
    Arrays.stream(messagesPanel.components)
        .filter { x: Component -> x === BlinkingCursorComponent.instance }
        .forEach { messagesPanel.remove(BlinkingCursorComponent.instance) }
    BlinkingCursorComponent.instance.timer.stop()
  }

  @RequiresEdt
  override fun refreshPanelsVisibility() {
    updateVisibilityOfContentPanels()
  }

  @RequiresEdt
  private fun sendChatMessage(project: Project) {
    val text = promptInput.getText()
    chatMessageHistory.messageSent(promptInput)
    sendMessage(project, text, "chat-question")
    promptInput.text = ""
  }

  @RequiresEdt
  private fun sendMessage(project: Project, message: String, recipeId: String) {
    if (!sendButton.isEnabled) {
      return
    }
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
          chat.sendMessageViaAgent(
              getClient(project),
              getInitializedServer(project),
              humanMessage,
              recipeId,
              this,
              inProgressChat)
        } catch (e: Exception) {
          logger.warn("Error sending message '$humanMessage' to chat", e)
        }
      } else {
        logger.warn("Agent is disabled, can't use chat.")
        addMessageToChat(
            ChatMessage(
                Speaker.ASSISTANT,
                "Cody is not able to reply at the moment. " +
                    "This is a bug, please report an issue to https://github.com/sourcegraph/sourcegraph/issues/new?template=jetbrains.md " +
                    "and include as many details as possible to help troubleshoot the problem."))
        finishMessageProcessing()
      }
      GraphQlLogger.logCodyEvent(this.project, "recipe:chat-question", "executed")
    }
  }

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

  fun focusPromptInput() {
    if (tabbedPane.selectedIndex == CHAT_TAB_INDEX) {
      promptInput.requestFocusInWindow()
      val textLength = promptInput.document.length
      promptInput.setCaretPosition(textLength)
    }
  }

  val preferredFocusableComponent: JComponent
    get() = promptInput

  companion object {
    const val ONBOARDING_PANEL = "onboardingPanel"
    const val CHAT_PANEL_INDEX = 0
    const val SIGN_IN_PANEL_INDEX = 1
    const val ONBOARDING_PANEL_INDEX = 2
    var logger = Logger.getInstance(CodyToolWindowContent::class.java)
    const val SING_IN_WITH_SOURCEGRAPH_PANEL = "singInWithSourcegraphPanel"
    private const val CHAT_TAB_INDEX = 0
    private const val RECIPES_TAB_INDEX = 1
    private const val CHAT_MESSAGE_HISTORY_CAPACITY = 100

    fun getInstance(project: Project): CodyToolWindowContent {
      return project.getService(CodyToolWindowContent::class.java)
    }
  }
}
