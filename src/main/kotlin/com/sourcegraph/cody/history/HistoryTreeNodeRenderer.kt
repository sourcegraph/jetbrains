package com.sourcegraph.cody.history

import com.intellij.ide.util.treeView.NodeRenderer
import com.intellij.ui.SimpleTextAttributes
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.history.util.DurationUnitFormatter
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.swing.JTree

class HistoryTreeNodeRenderer : NodeRenderer() {

  override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
  ) {
    when (value) {
      is HistoryTreeLeafNode -> {
        icon = Icons.Chat.ChatLeaf
        append(" ")
        append(value.getText())
        append(" ")

        val lastUpdated = value.chat.latestMessage()
        if (isShortDuration(lastUpdated)) {
                append(" ")
                val duration = DurationUnitFormatter.format(lastUpdated)
                append("$duration ago", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
      }
      else -> append(value.toString())
    }
  }

    private fun isShortDuration(since: LocalDateTime) =
        ChronoUnit.DAYS.between(since, LocalDateTime.now()).toInt() < 7
}
