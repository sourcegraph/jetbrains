package com.sourcegraph.cody.context.ui

import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.ToolbarDecorator.createDecorator
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.commit.NonModalCommitPanel.Companion.showAbove
import com.sourcegraph.cody.Icons.Chat
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.Repo
import com.sourcegraph.cody.chat.ChatSession
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.context.RemoteRepoUtils
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.state.EnhancedContextState
import com.sourcegraph.cody.history.state.RemoteRepositoryState
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.vcs.CodebaseName
import com.sourcegraph.vcs.convertGitCloneURLToCodebaseNameOrError
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultTreeModel

/**
 * A panel for configuring context in chats.
 */
abstract class EnhancedContextPanel(protected val project: Project, protected val chatSession: ChatSession) :
    JPanel() {
  companion object {
    fun create(project: Project, chatSession: ChatSession): EnhancedContextPanel {
      val isDotcomAccount = CodyAuthenticationManager.getInstance(project).getActiveAccount()?.isDotcomAccount() ?: false
      return if (isDotcomAccount) {
        ConsumerEnhancedContextPanel(project, chatSession)
      } else {
        EnterpriseEnhancedContextPanel(project, chatSession)
      }
    }
  }

  abstract val isEnhancedContextEnabled: Boolean

  fun setContextFromThisChatAsDefault() {
    ApplicationManager.getApplication().executeOnPooledThread {
      getContextState()?.let { HistoryService.getInstance(project).updateDefaultContextState(it) }
    }
  }

  protected fun getContextState(): EnhancedContextState? {
    val historyService = HistoryService.getInstance(project)
    return historyService.getContextReadOnly(chatSession.getInternalId())
      ?: historyService.getDefaultContextReadOnly()
  }

  protected fun updateContextState(modifyContext: (EnhancedContextState) -> Unit) {
    val contextState = getContextState() ?: EnhancedContextState()
    modifyContext(contextState)
    HistoryService.getInstance(project)
      .updateContextState(chatSession.getInternalId(), contextState)
    HistoryService.getInstance(project).updateDefaultContextState(contextState)
  }

  protected fun createToolbar(tree: JTree): ToolbarDecorator {
    return createDecorator(tree)
      .disableUpDownActions()
      .setToolbarPosition(ActionToolbarPosition.RIGHT)
      .setVisibleRowCount(1)
      .setScrollPaneBorder(BorderFactory.createEmptyBorder())
      .setToolbarBorder(BorderFactory.createEmptyBorder())
  }

  protected val treeRoot = CheckedTreeNode(CodyBundle.getString("context-panel.tree.root"))
  protected val treeModel = DefaultTreeModel(treeRoot)
  protected val tree = run {
    val checkPolicy =
      CheckboxTreeBase.CheckPolicy(
        /* checkChildrenWithCheckedParent = */ true,
        /* uncheckChildrenWithUncheckedParent = */ true,
        /* checkParentWithCheckedChild = */ true,
        /* uncheckParentWithUncheckedChild = */ false)
    CheckboxTree(ContextRepositoriesCheckboxRenderer(), treeRoot, checkPolicy)
  }

  init {
    layout = VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 0, true, false)
    tree.model = treeModel
  }

  abstract fun createPanel(): JComponent
  val panel = createPanel()

  init {
    // TODO: Resizing synchronously causes the element *now* under the pointer to get a click on mouse up, which can
    // check/uncheck a checkbox.
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
  override val isEnhancedContextEnabled: Boolean
    get() = false  // TODO: Make this toggle-able

  override fun createPanel(): JComponent {
    val toolbar = createToolbar(tree)
    // TODO: Add the "clock" ??? and "help" icons.
    // TODO: L10N
    toolbar.setEditActionName("Edit Remote Repositories")
    toolbar.setEditAction {
      val initialValue = getContextState()?.remoteRepositories?.map {
        it.codebaseName
      }?.joinToString("\n") ?: ""

      val controller = RemoteRepoPopupController(project)
      controller.onAccept = { repos ->
        repos.map {
          // TODO: Reset the repository list to this set.
        }
      }

      val popup = controller.createPopup(tree.width, initialValue)
      popup.showAbove(tree)
    }
    toolbar.addExtraAction(HelpButton())

    return toolbar.createPanel()
  }

  init {
    val repoNames =
      getContextState()
        ?.remoteRepositories
        ?.filter { it.isEnabled }
        ?.mapNotNull { it.codebaseName }
        ?: listOf()

    val remotesNode = ContextTreeRemotesNode()
    repoNames.forEach { repoName ->
      val node = ContextTreeRemoteRepoNode(RemoteRepo(repoName)) { checked -> println("repo $repoName checked? $checked") }
      remotesNode.add(node)
    }

    // TODO: L10N
    val endpoint = CodyAuthenticationManager.getInstance(project).getActiveAccount()?.server?.displayName ?: "endpoint"
    val contextRoot = ContextTreeEnterpriseRootNode(endpoint, repoNames.size) { checked ->
      println("checked: $checked")
    }
    contextRoot.add(remotesNode)
    contextRoot.isChecked = true // TODO

    treeRoot.add(contextRoot)
    treeModel.reload()
  }

  /* other deportees:

  private val remoteContextNode =
      ContextTreeRemoteRootNode(CodyBundle.getString("context-panel.tree.node-remote-repos"))


prepareTree:

    CodyAgentCodebase.getInstance(project).getUrl().thenApply { repoUrl ->
      val codebaseName = convertGitCloneURLToCodebaseNameOrError(repoUrl)
      RemoteRepoUtils.getRepositories(project, listOf(codebaseName))
          .completeOnTimeout(null, 15, TimeUnit.SECONDS)
          .thenApply { repos ->
            if (repos?.size == 1) {
              ApplicationManager.getApplication().invokeLater {
                addRemoteRepository(codebaseName)
              }
            }
          }
    }


  private fun getReposByUrlAndRun(
      codebaseNames: List<CodebaseName>,
      consumer: Consumer<List<Repo>>
  ) {
    RemoteRepoUtils.getRepositories(project, codebaseNames).thenApply { consumer.accept(it) }
  }

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

  @RequiresEdt
  private fun removeRemoteRepository(node: ContextTreeRemoteRepoNode) {
    updateContextState { contextState ->
      contextState.remoteRepositories.removeIf { it.codebaseName == node.codebaseName.value }
    }
    remoteContextNode.remove(node)
    if (enhancedContextNode.children().toList().contains(remoteContextNode) &&
        !remoteContextNode.children().hasMoreElements()) {
      enhancedContextNode.remove(remoteContextNode)
    }
    treeModel.reload()
    disableRemote(node.codebaseName)
  }

  @RequiresEdt
  private fun addRemoteRepository(codebaseName: CodebaseName, isCheckedInitially: Boolean = true) {

    updateContextState { contextState ->
      val repositories = contextState.remoteRepositories
      val existingRepo = repositories.find { it.codebaseName == codebaseName.value }
      val modifiedRepo = existingRepo ?: RemoteRepositoryState()
      modifiedRepo.codebaseName = codebaseName.value
      modifiedRepo.isEnabled = isCheckedInitially
      if (existingRepo == null) repositories.add(modifiedRepo)
    }

    val existingRemoteNode =
        remoteContextNode.children().toList().filterIsInstance<ContextTreeRemoteRepoNode>().find {
          it.codebaseName == codebaseName
        }

    if (existingRemoteNode != null) {
      existingRemoteNode.isChecked = isCheckedInitially
    } else {
      val remoteRepoNode =
          ContextTreeRemoteRepoNode(codebaseName) { isChecked ->
            if (isChecked) enableRemote(codebaseName) else disableRemote(codebaseName)
          }
      remoteRepoNode.isChecked = isCheckedInitially
      remoteContextNode.add(remoteRepoNode)
      if (!enhancedContextNode.children().toList().contains(remoteContextNode)) {
        enhancedContextNode.add(remoteContextNode)
      }
      treeModel.reload()
    }
  }
 */
}

class ConsumerEnhancedContextPanel(project: Project, chatSession: ChatSession) : EnhancedContextPanel(project, chatSession) {
  override val isEnhancedContextEnabled: Boolean
    get() = enhancedContextEnabled.get()

  private val enhancedContextEnabled = AtomicBoolean(true)

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
    val toolbar = createToolbar(tree)
    toolbar.addExtraAction(ReindexButton(project))
    toolbar.addExtraAction(HelpButton())
    return toolbar.createPanel()
  }

  init {
    prepareTree()
  }
}
