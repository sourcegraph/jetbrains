package com.sourcegraph.cody.history

import com.intellij.ui.treeStructure.Tree
import com.sourcegraph.cody.history.listener.DeleteListener
import com.sourcegraph.cody.history.listener.DoubleLeftClickListener
import com.sourcegraph.cody.history.listener.EnterListener
import com.sourcegraph.cody.history.listener.RightClickListener
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.history.util.ToolbarDurationTextFormatter
import java.awt.event.MouseEvent
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class HistoryTree(onSelect: (ChatState) -> Unit, onDelete: (ChatState) -> Unit) : Tree() {

  private val popup = HistoryPopupMenu(onSelect, onDelete, getSelected = { getLeafOrNull()!!.chat })

  init {
    isRootVisible = false
    addMouseListener(DoubleLeftClickListener { invokeOnlyOnLeaf { onSelect(it) } })
    addMouseListener(
        RightClickListener { mouse -> invokeOnlyOnLeaf { popup.show(this, mouse.x, mouse.y) } })
    addKeyListener(EnterListener { invokeOnlyOnLeaf { onSelect(it) } })
    addKeyListener(DeleteListener { invokeOnlyOnLeaf { onDelete(it) } })
  }

  override fun getToolTipText(event: MouseEvent): String {
    val leaf = getPathForLocation(event.x, event.y)?.lastPathComponent as? ChatLeafNode
    if (leaf != null) {
      val duration =
          ToolbarDurationTextFormatter.formatDuration(
              leaf.chat.latestMessage(), LocalDateTime.now())
      return "Last updated: $duration ago"
    }
    return ""
  }

  fun refreshTree() {
    val root = DefaultMutableTreeNode()
    for ((period, chats) in getChatsGroupedByPeriod()) {
      val periodNode = DefaultMutableTreeNode(period)
      for (chat in chats) {
        periodNode.add(ChatLeafNode(chat))
      }
      root.add(periodNode)
    }
    model = DefaultTreeModel(root, false)
  }

  private fun invokeOnlyOnLeaf(action: (ChatState) -> Unit) {
    val leaf = getLeafOrNull()
    if (leaf != null) action(leaf.chat)
  }

  private fun getLeafOrNull(): ChatLeafNode? = selectionPath?.lastPathComponent as? ChatLeafNode

  private fun getChatsGroupedByPeriod(): Map<String, List<ChatState>> =
      HistoryService.getInstance()
          .state
          .chats
          .sortedByDescending { chat -> chat.latestMessage() }
          .groupBy { chat -> getPeriodText(chat.latestMessage()) }

  private fun getPeriodText(date: LocalDateTime): String {
    val today = LocalDateTime.now()
    val daysBetween = ChronoUnit.DAYS.between(date, today).toInt()
    return when {
      daysBetween == 0 -> "Today"
      daysBetween == 1 -> "Yesterday"
      daysBetween in 2..6 -> "$daysBetween days ago"
      date.isAfter(today.minusWeeks(1)) -> "Last week"
      date.isAfter(today.minusMonths(1)) -> "Last month"
      date.isAfter(today.minusYears(1)) -> "Last year"
      else -> "Older"
    }
  }

  private class ChatLeafNode(val chat: ChatState) : DefaultMutableTreeNode(chat, false) {

    override fun toString(): String = chat.title()
  }
}
