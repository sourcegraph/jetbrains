package com.sourcegraph.cody.history

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.history.util.DurationGroupFormatter
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class HistoryTree(
    private val onSelect: (ChatState) -> Unit,
    private val onDelete: (ChatState) -> Unit
) : SimpleToolWindowPanel(true, true) {

  private val model = DefaultTreeModel(buildTree())
  private val tree =
      SimpleTree(model).apply {
        isRootVisible = false
        cellRenderer = HistoryTreeNodeRenderer()
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_MAP_KEY)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), DELETE_MAP_KEY)
        actionMap.put(ENTER_MAP_KEY, ActionWrapper(::selectSelected))
        actionMap.put(DELETE_MAP_KEY, ActionWrapper(::deleteSelected))
      }

  init {
    val group = DefaultActionGroup()
    group.add(LeafPopupAction(tree, "Select Chat", null, ::selectSelected))
    group.addSeparator()
    group.add(LeafPopupAction(tree, "Remove Chat", AllIcons.Actions.GC, ::deleteSelected))
    PopupHandler.installPopupMenu(tree, group, "ChatActionsPopup")
    EditSourceOnDoubleClickHandler.install(tree, ::selectSelected)
    setContent(ScrollPaneFactory.createScrollPane(tree))
  }

  fun refreshTree() {
    // todo: rename to "notifyAdded" and use in such context when model is changed
    // todo: this method must be executed when a new chat is added
    // todo: use model.insertNodeInto(child, parent)
  }

  private fun selectSelected() {
    val leaf = tree.selectedLeafOrNull()
    if (leaf != null) onSelect(leaf.chat)
  }

  private fun deleteSelected() {
    val leaf = tree.selectedLeafOrNull()
    if (leaf != null) {
      onDelete(leaf.chat)
      model.removeNodeFromParent(leaf)
    }
  }

  private fun buildTree(): DefaultMutableTreeNode {
    val root = DefaultMutableTreeNode()
    for ((period, chats) in getChatsGroupedByPeriod()) {
      val periodNode = DefaultMutableTreeNode(period)
      for (chat in chats) {
        periodNode.add(HistoryTreeLeafNode(chat))
      }
      root.add(periodNode)
    }
    return root
  }

  private fun getChatsGroupedByPeriod(): Map<String, List<ChatState>> =
      HistoryService.getInstance()
          .state
          .chats
          .sortedByDescending { chat -> chat.latestMessage() }
          .groupBy { chat -> DurationGroupFormatter.format(chat.latestMessage()) }

  private class LeafPopupAction(
      private val tree: SimpleTree,
      text: String,
      icon: Icon? = null,
      private val action: () -> Unit
  ) : AnAction(text, null, icon) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = tree.selectedLeafOrNull() != null
    }

    override fun actionPerformed(event: AnActionEvent) {
      action()
    }
  }

  private class ActionWrapper(private val action: () -> Unit) : AbstractAction() {
    override fun actionPerformed(p0: ActionEvent?) {
      action()
    }
  }

  private companion object {

    private const val ENTER_MAP_KEY = "enter"
    private const val DELETE_MAP_KEY = "delete"

    private fun SimpleTree.selectedLeafOrNull() =
        selectionPath?.lastPathComponent as? HistoryTreeLeafNode
  }
}
