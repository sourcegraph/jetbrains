package com.sourcegraph.cody.history

import com.intellij.ui.components.JBList
import com.sourcegraph.cody.history.listener.DoubleClickListener
import com.sourcegraph.cody.history.listener.EnterListener
import com.sourcegraph.cody.history.util.ToolbarDurationTextFactory
import java.awt.event.MouseEvent
import java.time.LocalDateTime

class HistoryList(
        private val onClick: (selected: HistoryListItem) -> Unit,
) : JBList<HistoryListItem>() {

  private var lastTooltipIndex = -1

  init {
    addMouseListener(DoubleClickListener { onClick(selectedValue) })
    addKeyListener(EnterListener { onClick(selectedValue) })
  }

  override fun getToolTipText(event: MouseEvent?): String {
    val index = locationToIndex(event!!.point)
    if (index >= 0) {
      val changed = lastTooltipIndex != index
      if (changed) {
        lastTooltipIndex = index
        return "" // hide on-change to repaint on new position
      }
      val item = model.getElementAt(index)
      val duration =
          ToolbarDurationTextFactory.getDurationText(item.lastUpdated, LocalDateTime.now())
      return "Last updated: $duration ago"
    }
    return ""
  }
}
