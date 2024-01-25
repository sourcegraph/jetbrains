package com.sourcegraph.cody

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.chat.SignInWithSourcegraphPanel
import com.sourcegraph.cody.chat.ui.ChatPanel
import com.sourcegraph.cody.chat.ui.CodyOnboardingGuidancePanel
import com.sourcegraph.cody.commands.CommandId
import com.sourcegraph.cody.commands.ui.CommandsTabPanel
import com.sourcegraph.cody.config.CodyAccount
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.vscode.CancellationToken
import java.awt.CardLayout
import java.awt.Component
import java.util.concurrent.ExecutionException
import java.util.function.Consumer
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
class CodyToolWindowContent(private val project: Project) {
  private val allContentLayout = CardLayout()
  val allContentPanel = JPanel(allContentLayout)

  private var codyOnboardingGuidancePanel: CodyOnboardingGuidancePanel? = null
  private val signInWithSourcegraphPanel = SignInWithSourcegraphPanel(project)
  private val historyTree = JPanel() // HistoryTree(::selectChat, ::deleteChat)
  private val tabbedPane = JBTabbedPane()

  private val chatPanel = JPanel(CardLayout())
  private var chatPanelEmbedded = ChatPanel(project, "default")

  private val commandsPanel =
      CommandsTabPanel(project) { cancellationToken: CancellationToken, cmdId: CommandId ->
        ApplicationManager.getApplication().invokeLater {
          // sendMessage(project, cancellationToken, cmdId.displayName, cmdId)
        }
      }

  private val subscriptionPanel = SubscriptionTabPanel()

  init {
    tabbedPane.insertSimpleTab("Chat", chatPanel, CHAT_TAB_INDEX)
    tabbedPane.insertSimpleTab("Chat History", historyTree, HISTORY_TAB_INDEX)
    tabbedPane.insertSimpleTab("Commands", commandsPanel, RECIPES_TAB_INDEX)
    tabbedPane.insertSimpleTab("Subscription", subscriptionPanel, SUBSCRIPTION_TAB_INDEX)

    tabbedPane.addChangeListener {
      if (tabbedPane.selectedIndex == CHAT_TAB_INDEX) {
        chatPanelEmbedded.requestFocusOnInputPrompt()
      }
    }

    chatPanel.add(chatPanelEmbedded)

    allContentPanel.add(tabbedPane, "tabbedPane", CHAT_PANEL_INDEX)
    allContentPanel.add(signInWithSourcegraphPanel, SIGN_IN_PANEL, SIGN_IN_PANEL_INDEX)
    allContentLayout.show(allContentPanel, SIGN_IN_PANEL)

    ApplicationManager.getApplication().invokeLater { refreshPanelsVisibility() }
    ApplicationManager.getApplication().executeOnPooledThread { refreshSubscriptionTab() }

    startNewChat(project) { panelId ->
      ApplicationManager.getApplication().invokeLater {
        chatPanelEmbedded = ChatPanel(project, panelId)
        chatPanel.removeAll()
        chatPanel.add(chatPanelEmbedded)
      }
    }

    //    refreshChatToEmpty()
    //    historyTree.refreshTree()
  }

  @RequiresBackgroundThread
  fun refreshSubscriptionTab() {
    CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
      val data = fetchSubscriptionPanelData(project, agent.server)
      if (data != null) {
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
    }
  }

  fun startNewChat(project: Project, onNewChatId: Consumer<String>) {
    CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
      try {
        onNewChatId.accept(agent.server.chatNew().get())
      } catch (e: ExecutionException) {
        // Agent cannot gracefully recover when connection is lost, we need to restart it
        // TODO https://github.com/sourcegraph/jetbrains/issues/306
        CodyToolWindowContent.logger.warn("Failed to load new chat, restarting agent", e)
        CodyAgentService.getInstance(project).restartAgent(project)
        Thread.sleep(5000)
        startNewChat(project, onNewChatId)
      }
    }
  }

  //  fun refreshChatToEmpty() {
  //    CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
  //      ApplicationManager.getApplication().invokeLater {
  //        messagesPanel.removeAll()
  //        chatMessageHistory.clearHistory()
  //        inProgressChat.abort()
  //        addWelcomeMessage()
  //        activateChatTab()
  //      }
  //      chatId = agent.server.chatNew().get()
  //    }
  //  }

  //  private fun deleteChat(item: ChatState) {
  //    HistoryService.getInstance().removeChat(item.panelId!!)
  //    if (chatId == item.panelId) {
  //      refreshChatToEmpty()
  //    }
  //    historyTree.refreshTree()
  //  }

  @RequiresEdt
  fun refreshPanelsVisibility() {
    val codyAuthenticationManager = CodyAuthenticationManager.instance
    if (codyAuthenticationManager.getAccounts().isEmpty()) {
      allContentLayout.show(allContentPanel, SIGN_IN_PANEL)
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
      return
    }
    allContentLayout.show(allContentPanel, "tabbedPane")
  }

  private fun activateChatTab() {
    tabbedPane.selectedIndex = CHAT_TAB_INDEX
  }

  companion object {
    const val ONBOARDING_PANEL = "onboardingPanel"
    const val SIGN_IN_PANEL = "singInWithSourcegraphPanel"

    const val CHAT_PANEL_INDEX = 0
    const val SIGN_IN_PANEL_INDEX = 1
    const val ONBOARDING_PANEL_INDEX = 2

    private const val CHAT_TAB_INDEX = 0
    private const val HISTORY_TAB_INDEX = 1
    private const val RECIPES_TAB_INDEX = 2
    private const val SUBSCRIPTION_TAB_INDEX = 3

    var logger = Logger.getInstance(CodyToolWindowContent::class.java)

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
