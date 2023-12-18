package com.sourcegraph.cody.history

import com.intellij.ui.components.JBList
import com.sourcegraph.cody.history.listener.DoubleLeftClickListener
import com.sourcegraph.cody.history.listener.EnterListener
import com.sourcegraph.cody.history.listener.RightClickListener
import com.sourcegraph.cody.history.util.ToolbarDurationTextFactory
import java.awt.event.MouseEvent
import java.time.LocalDateTime

class HistoryList(
        private val onSelect: (selected: HistoryListItem) -> Unit,
) : JBList<HistoryListItem>() {

  private var lastTooltipIndex = -1

  init {
    val popup = HistoryPopupMenu(
            onSelect = { onSelect(selectedValue) },
            onDelete = { HistoryService.getInstance().deleteChat(selectedValue.chatId) }
    )
    addMouseListener(DoubleLeftClickListener { onSelect(selectedValue) })
    addMouseListener(RightClickListener { event ->
      selectedIndex = locationToIndex(event.point)
      popup.show(this, event.x, event.y)
    })
    addKeyListener(EnterListener { onSelect(selectedValue) })
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
