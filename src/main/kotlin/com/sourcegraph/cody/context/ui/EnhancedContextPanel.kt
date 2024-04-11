package com.sourcegraph.cody.context.ui

import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.ui.getTreePath
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.ToolbarDecorator.createDecorator
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.commit.NonModalCommitPanel.Companion.showAbove
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.chat.ChatSession
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.context.RemoteRepo
import com.sourcegraph.cody.context.RemoteRepoUtils
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.state.EnhancedContextState
import com.sourcegraph.cody.history.state.RemoteRepositoryState
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.vcs.CodebaseName
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultTreeModel

/**
 * A panel for configuring context in chats. Consumer and Enterprise context panels are designed around a tree whose
 * layout grows and shrinks as the tree view nodes are expanded and collapsed.
 */
abstract class EnhancedContextPanel(protected val project: Project, protected val chatSession: ChatSession) :
    JPanel() {
  companion object {
    /**
     * Creates an EnhancedContextPanel for `chatSession`.
     */
    fun create(project: Project, chatSession: ChatSession): EnhancedContextPanel {
      val isDotcomAccount = CodyAuthenticationManager.getInstance(project).getActiveAccount()?.isDotcomAccount() ?: false
      return if (isDotcomAccount) {
        ConsumerEnhancedContextPanel(project, chatSession)
      } else {
        EnterpriseEnhancedContextPanel(project, chatSession)
      }
    }
  }

  /**
   * Gets whether enhanced context is enabled.
   */
  val isEnhancedContextEnabled: Boolean
    get() = enhancedContextEnabled.get()

  /**
   * Whether enhanced context is enabled. Set this when enhance context is toggled in the panel UI. This is read on
   * background threads by `isEnhancedContextEnabled`.
   */
  protected val enhancedContextEnabled = AtomicBoolean(true)

  /**
   * Sets this EnhancedContextPanel's configuration as the project's default enhanced context state.
   */
  fun setContextFromThisChatAsDefault() {
    ApplicationManager.getApplication().executeOnPooledThread {
      getContextState()?.let { HistoryService.getInstance(project).updateDefaultContextState(it) }
    }
  }

  /**
   * Gets the chat session's enhanced context state.
   */
  protected fun getContextState(): EnhancedContextState? {
    val historyService = HistoryService.getInstance(project)
    return historyService.getContextReadOnly(chatSession.getInternalId())
      ?: historyService.getDefaultContextReadOnly()
  }

  /**
   * Reads, modifies, and writes back the chat's enhanced context state.
   */
  protected fun updateContextState(modifyContext: (EnhancedContextState) -> Unit) {
    val contextState = getContextState() ?: EnhancedContextState()
    modifyContext(contextState)
    HistoryService.getInstance(project)
      .updateContextState(chatSession.getInternalId(), contextState)
    HistoryService.getInstance(project).updateDefaultContextState(contextState)
  }

  /**
   * Creates the ToolbarDecorator for the panel. The returned toolbar decorator does not have any buttons.
   */
  protected fun createToolbar(): ToolbarDecorator {
    return createDecorator(tree)
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.RIGHT)
      .setVisibleRowCount(1)
      .setScrollPaneBorder(BorderFactory.createEmptyBorder())
      .setToolbarBorder(BorderFactory.createEmptyBorder())
  }

  /**
   * The root node of the tree view. This node is not visible. Add entries to the enhanced context treeview as roots
   * of this node.
   */
  protected val treeRoot = CheckedTreeNode(CodyBundle.getString("context-panel.tree.root"))

  /**
   * The mutable model of tree nodes. Call `treeModel.reload()`, etc. when the tree model changes.
   */
  protected val treeModel = DefaultTreeModel(treeRoot)

  /**
   * The tree component.
   */
  protected val tree = run {
    val checkPolicy =
      CheckboxTreeBase.CheckPolicy(
        /* checkChildrenWithCheckedParent = */ true,
        /* uncheckChildrenWithUncheckedParent = */ true,
        /* checkParentWithCheckedChild = */ true,
        /* uncheckParentWithUncheckedChild = */ false)
    object : CheckboxTree(ContextRepositoriesCheckboxRenderer(), treeRoot, checkPolicy) {
      // When collapsed, the horizontal scrollbar obscures the Chat Context summary & checkbox. Prefer to clip. Users
      // can resize the sidebar if desired.
      override fun getScrollableTracksViewportWidth(): Boolean = true
    }
  }

  init {
    layout = VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 0, true, false)
    tree.model = treeModel
  }

  abstract fun createPanel(): JComponent
  val panel = createPanel()

  init {
    // TODO: Resizing synchronously causes the element *now* under the pointer to get a click on mouse up, which can
    // check/uncheck a checkbox you were not aiming at.
    tree.addTreeExpansionListener(
      object : TreeExpansionListener {
        override fun treeExpanded(event: TreeExpansionEvent) {
          if (event.path.pathCount == 2) {
            // The top-level node was expanded, so expand the entire tree.
            expandAllNodes()
          }
          resize()
        }

        override fun treeCollapsed(event: TreeExpansionEvent) {
          resize()
        }
      })

    add(panel)
  }

  /**
   * Adjusts the layout to accommodate the expanded rows in the treeview, and revalidates layout.
   */
  @RequiresEdt
  protected fun resize() {
    val padding = 5
    panel.preferredSize =
      Dimension(0, padding + tree.rowCount * tree.rowHeight)
    panel.parent?.revalidate()
  }

  @RequiresEdt
  private fun expandAllNodes(rowCount: Int = tree.rowCount) {
    for (i in 0 until tree.rowCount) {
      tree.expandRow(i)
    }

    if (tree.getRowCount() != rowCount) {
      expandAllNodes(tree.rowCount)
    }
  }
}

class EnterpriseEnhancedContextPanel(project: Project, chatSession: ChatSession): EnhancedContextPanel(project, chatSession) {
  // Cache the raw user input so the user can reopen the popup to make corrections without starting from scratch.
  private var rawSpec: String = ""

  override fun createPanel(): JComponent {
    val toolbar = createToolbar()
    // TODO: Add the "clock" button when the functionality is clarified.
    // TODO: L10N
    toolbar.setEditActionName("Edit Remote Repositories")
    toolbar.setEditAction {
      val controller = RemoteRepoPopupController(project)
      controller.onAccept = { spec ->
        rawSpec = spec
        val repos = spec.split(Regex("""\s+""")).toSet()
        RemoteRepoUtils.getRepositories(project, repos.map { it -> CodebaseName(it) }.toList())
          .completeOnTimeout(null, 15, TimeUnit.SECONDS)
          .thenApply { repos ->
            if (repos == null) {
              // TODO: Show an error notification, balloon, or tool window balloon.
              return@thenApply
            }
            var trimmedRepos = repos.take(MAX_REMOTE_REPOSITORY_COUNT)
            runInEdt {
              // Update the extension-side state.
              chatSession.sendWebviewMessage(
                WebviewMessage(command = "context/choose-remote-search-repo", explicitRepos = trimmedRepos))

              // Update the plugin's copy of the state. :(
              updateContextState { state ->
                state.remoteRepositories.clear()
                state.remoteRepositories.addAll(
                  trimmedRepos.map { repo ->
                    RemoteRepositoryState().apply {
                      codebaseName = repo.name
                      isEnabled = true
                    }
                  }
                )
              }

              // Update the UI.
              // TODO: Pass in the checked/unchecked state.
              updateTree(trimmedRepos.map { it -> it.name })
              resize()
            }
          }
      }

      val popup = controller.createPopup(tree.width, rawSpec)
      popup.showAbove(tree)
    }
    toolbar.addExtraAction(HelpButton())
    return toolbar.createPanel()
  }

  private val remotesNode = ContextTreeRemotesNode()
  private val contextRoot = object : ContextTreeEnterpriseRootNode("", 0, { checked ->
    enhancedContextEnabled.set(checked)
  }) {
    override fun isChecked(): Boolean {
       return enhancedContextEnabled.get()
    }
  }

  init {
    val contextState = getContextState()

    rawSpec = contextState?.remoteRepositories?.map {
      it.codebaseName
    }?.joinToString("\n") ?: ""

    // TODO: L10N
    val endpoint = CodyAuthenticationManager.getInstance(project).getActiveAccount()?.server?.displayName ?: "endpoint"
    contextRoot.endpointName = endpoint
    contextRoot.add(remotesNode)
    contextRoot.isChecked = true // TODO

    val repoNames =
      contextState
        ?.remoteRepositories
        ?.filter { it.isEnabled }
        ?.mapNotNull { it.codebaseName }
        ?: listOf()
    updateTree(repoNames)

    treeRoot.add(contextRoot)
    treeModel.reload()
    resize()
  }

  private fun updateTree(repoNames: List<String>) {
    val remotesPath = treeModel.getTreePath(remotesNode.userObject)
    val wasExpanded = remotesPath != null && tree.isExpanded(remotesPath)
    remotesNode.removeAllChildren()
    repoNames.forEach { repoName ->
      val node = ContextTreeRemoteRepoNode(RemoteRepo(repoName)) { checked -> println("repo $repoName checked? $checked") }
      remotesNode.add(node)
    }
    contextRoot.numRepos = repoNames.size
    treeModel.reload(contextRoot)
    if (wasExpanded) {
      tree.expandPath(remotesPath)
    }
  }

  /* other deportees:

  private fun enableRemote(codebaseName: CodebaseName) {
    updateContextState { contextState ->
      contextState.remoteRepositories.find { it.codebaseName == codebaseName.value }?.isEnabled =
          true
    }

    val enabledCodebases =
        getContextState()
            ?.remoteRepositories
            ?.filter { it.isEnabled }
            ?.mapNotNull { it.codebaseName }
            ?.map { CodebaseName(it) } ?: listOf()

    getReposByUrlAndRun(enabledCodebases) { repos ->
      chatSession.sendWebviewMessage(
          WebviewMessage(command = "context/choose-remote-search-repo", explicitRepos = repos))
    }
  }

  @RequiresEdt
  private fun disableRemote(codebaseName: CodebaseName) {
    updateContextState { contextState ->
      contextState.remoteRepositories.find { it.codebaseName == codebaseName.value }?.isEnabled =
          false
    }

    getReposByUrlAndRun(listOf(codebaseName)) { repos ->
      repos.firstOrNull()?.let { repo ->
        chatSession.sendWebviewMessage(
            WebviewMessage(command = "context/remove-remote-search-repo", repoId = repo.id))
      }
    }
  }

 */
}

class ConsumerEnhancedContextPanel(project: Project, chatSession: ChatSession) : EnhancedContextPanel(project, chatSession) {
  private val enhancedContextNode =
      ContextTreeRootNode(CodyBundle.getString("context-panel.tree.node-chat-context")) { isChecked
        ->
        enhancedContextEnabled.set(isChecked)
        updateContextState { it.isEnabled = isChecked }
      }

  private val localContextNode =
      ContextTreeLocalRootNode(
          CodyBundle.getString("context-panel.tree.node-local-project"), enhancedContextEnabled)
  private val localProjectNode = ContextTreeLocalRepoNode(project, enhancedContextEnabled)

  @RequiresEdt
  private fun prepareTree() {
    treeRoot.add(enhancedContextNode)
    localContextNode.add(localProjectNode)
    enhancedContextNode.add(localContextNode)

    val contextState = getContextState()
    ApplicationManager.getApplication().invokeLater {
      enhancedContextNode.isChecked = contextState?.isEnabled ?: true
    }

    treeModel.reload()
    resize()
  }

  override fun createPanel(): JComponent {
    val toolbar = createToolbar()
    toolbar.addExtraAction(ReindexButton(project))
    toolbar.addExtraAction(HelpButton())
    return toolbar.createPanel()
  }

  init {
    prepareTree()
  }
}
