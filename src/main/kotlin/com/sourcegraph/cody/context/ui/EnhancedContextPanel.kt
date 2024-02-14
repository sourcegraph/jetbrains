package com.sourcegraph.cody.context.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ToolbarDecorator.createDecorator
import com.intellij.util.concurrency.annotations.RequiresEdt
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
import com.sourcegraph.vcs.convertGitCloneURLToCodebaseNameOrError
import java.awt.Dimension
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultTreeModel

class EnhancedContextPanel(private val project: Project, private val chatSession: ChatSession) :
    JPanel() {

  val isEnhancedContextEnabled = AtomicBoolean(true)

  private val treeRoot = CheckedTreeNode(CodyBundle.getString("context-panel.tree.root"))
  private val enhancedContextNode =
      ContextTreeRootNode(CodyBundle.getString("context-panel.tree.node-chat-context")) { isChecked
        ->
        isEnhancedContextEnabled.set(isChecked)
        updateContextState { it.isEnabled = isChecked }
      }
  private val localContextNode =
      ContextTreeRootNode(
          CodyBundle.getString("context-panel.tree.node-local-project"), isEnabled = false)
  private val remoteContextNode =
      ContextTreeRootNode(CodyBundle.getString("context-panel.tree.node-remote-repos"))
  private val localProjectNode = ContextTreeLocalRepoNode(project)

  private val treeModel = DefaultTreeModel(treeRoot)

  private val tree = run {
    val checkboxPropagationPolicy =
        CheckboxTreeBase.CheckPolicy(
            /* checkChildrenWithCheckedParent = */ true,
            /* uncheckChildrenWithUncheckedParent = */ true,
            /* checkParentWithCheckedChild = */ false,
            /* uncheckParentWithUncheckedChild = */ false)
    CheckboxTree(ContextRepositoriesCheckboxRenderer(), treeRoot, checkboxPropagationPolicy)
  }

  fun setAsActive() {
    ApplicationManager.getApplication().executeOnPooledThread {
      getContextState()?.let { HistoryService.getInstance(project).updateDefaultContextState(it) }
    }
  }

  @RequiresEdt
  private fun prepareTree() {
    treeRoot.add(enhancedContextNode)
    localContextNode.add(localProjectNode)
    enhancedContextNode.add(localContextNode)

    val contextState = getContextState()

    ApplicationManager.getApplication().invokeLater {
      enhancedContextNode.isChecked = contextState?.isEnabled ?: true
      localContextNode.isChecked = contextState?.isEnabled ?: true
      localProjectNode.isChecked = contextState?.isEnabled ?: true
    }

    if (!isDotComAccount()) {
      if (contextState != null) {
        contextState.remoteRepositories.forEach { repo ->
          repo.remoteUrl?.let { remoteUrl -> addRemoteRepository(remoteUrl, repo.isEnabled) }
        }
      } else {
        CodyAgentCodebase.getInstance(project).getUrl().thenApply { repoUrl ->
          val codebaseName = convertGitCloneURLToCodebaseNameOrError(repoUrl)
          RemoteRepoUtils.getRepository(project, codebaseName)
              .completeOnTimeout(null, 15, TimeUnit.SECONDS)
              .thenApply { repo ->
                if (repo != null) {
                  ApplicationManager.getApplication().invokeLater { addRemoteRepository(repoUrl) }
                }
              }
        }
      }
    }

    treeModel.reload()
  }

  private fun getContextState(): EnhancedContextState? {
    val historyService = HistoryService.getInstance(project)

    return historyService.getContextReadOnly(chatSession.getInternalId())
        ?: historyService.getDefaultContextReadOnly()
  }

  private fun updateContextState(modifyContext: (EnhancedContextState) -> Unit) {
    val contextState = getContextState() ?: EnhancedContextState()
    modifyContext(contextState)
    HistoryService.getInstance(project)
        .updateContextState(chatSession.getInternalId(), contextState)
    HistoryService.getInstance(project).updateDefaultContextState(contextState)
  }

  private fun isDotComAccount() =
      CodyAuthenticationManager.instance.getActiveAccount(project)?.isDotcomAccount() ?: false

  private fun getRepoByUrlAndRun(codebaseName: String, consumer: Consumer<Repo>) {
    RemoteRepoUtils.getRepository(project, codebaseName).thenApply {
      it?.let { repo -> consumer.accept(repo) }
    }
  }

  private fun enableRemote(codebaseName: String) {
    updateContextState { contextState ->
      contextState.remoteRepositories.find { it.remoteUrl == codebaseName }?.isEnabled = true
    }
    getRepoByUrlAndRun(codebaseName) { repo ->
      chatSession.sendWebviewMessage(
          WebviewMessage(
              command = "context/choose-remote-search-repo", explicitRepos = listOf(repo)))
    }
  }

  private fun disableRemote(codebaseName: String) {
    updateContextState { contextState ->
      contextState.remoteRepositories.find { it.remoteUrl == codebaseName }?.isEnabled = false
    }
    getRepoByUrlAndRun(codebaseName) { repo ->
      chatSession.sendWebviewMessage(
          WebviewMessage(command = "context/remove-remote-search-repo", repoId = repo.id))
    }
  }

  @RequiresEdt
  private fun removeRemoteRepository(node: ContextTreeRemoteRepoCodebaseNameNode) {
    updateContextState { contextState ->
      contextState.remoteRepositories.removeIf { it.remoteUrl == node.codebaseName }
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
  private fun addRemoteRepository(repoUrl: String, isCheckedInitially: Boolean = true) {
    val codebaseName = convertGitCloneURLToCodebaseNameOrError(repoUrl)

    updateContextState { contextState ->
      val repositories = contextState.remoteRepositories
      val existingRepo = repositories.find { it.remoteUrl == codebaseName }
      val modifiedRepo = existingRepo ?: RemoteRepositoryState()
      modifiedRepo.remoteUrl = codebaseName
      modifiedRepo.isEnabled = isCheckedInitially
      if (existingRepo == null) repositories.add(modifiedRepo)
    }

    val existingRemoteNode =
        remoteContextNode
            .children()
            .toList()
            .filterIsInstance<ContextTreeRemoteRepoCodebaseNameNode>()
            .find { it.codebaseName == codebaseName }

    if (existingRemoteNode != null) {
      existingRemoteNode.isChecked = isCheckedInitially
    } else {
      val remoteRepoNode =
          ContextTreeRemoteRepoCodebaseNameNode(codebaseName) { isChecked ->
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

  @RequiresEdt
  private fun expandAllNodes(rowCount: Int = tree.rowCount) {
    for (i in 0 until tree.rowCount) {
      tree.expandRow(i)
    }

    if (tree.getRowCount() != rowCount) {
      expandAllNodes(tree.rowCount)
    }
  }

  init {
    layout = VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 0, true, false)
    tree.setModel(treeModel)
    prepareTree()

    val toolbarDecorator =
        createDecorator(tree)
            .disableUpDownActions()
            .setVisibleRowCount(1)
            .setScrollPaneBorder(BorderFactory.createEmptyBorder())
            .setToolbarBorder(BorderFactory.createEmptyBorder())

    if (!isDotComAccount()) {
      toolbarDecorator.setAddActionName(
          CodyBundle.getString("context-panel.button.add-remote-repo"))
      toolbarDecorator.setAddAction {
        AddRepositoryDialog(project) { repoUrl -> addRemoteRepository(repoUrl) }.show()
        expandAllNodes()
      }

      toolbarDecorator.setRemoveActionName(
          CodyBundle.getString("context-panel.button.remove-remote-repo"))
      toolbarDecorator.setRemoveActionUpdater {
        tree.selectionPath?.lastPathComponent is ContextTreeRemoteRepoCodebaseNameNode
      }
      toolbarDecorator.setRemoveAction {
        (tree.selectionPath?.lastPathComponent as? ContextTreeRemoteRepoCodebaseNameNode)?.let {
            node ->
          removeRemoteRepository(node)
          expandAllNodes()
        }
      }
    }

    toolbarDecorator.addExtraAction(ReindexButton(project))
    toolbarDecorator.addExtraAction(HelpButton())

    val panel = toolbarDecorator.createPanel()

    tree.addTreeExpansionListener(
        object : TreeExpansionListener {
          private fun resize() {
            val padding = 5
            val actionsPanelHeight = toolbarDecorator.actionsPanel.height
            panel.preferredSize =
                Dimension(0, padding + actionsPanelHeight + tree.rowCount * tree.rowHeight)
            panel.parent.revalidate()
          }

          override fun treeExpanded(event: TreeExpansionEvent) {
            val component = event.path.lastPathComponent
            if (component is ContextTreeRootNode && component == enhancedContextNode) {
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
}
