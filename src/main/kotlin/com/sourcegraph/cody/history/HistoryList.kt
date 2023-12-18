package com.sourcegraph.cody.history

import com.intellij.ui.components.JBList
import com.sourcegraph.cody.history.listener.DoubleClickListener
import com.sourcegraph.cody.history.listener.EnterListener
import java.awt.event.MouseEvent
import java.time.Duration
import java.time.LocalDateTime
import kotlin.time.DurationUnit
import kotlin.time.toKotlinDuration

class HistoryList(private val onSelected: (selected: HistoryListItem) -> Unit) :
    JBList<HistoryListItem>() {

  private var lastIndex = -1

  init {
    addMouseListener(DoubleClickListener { onSelected(selectedValue) })
    addKeyListener(EnterListener { onSelected(selectedValue) })
  }

  override fun getToolTipText(event: MouseEvent?): String {
    val index = locationToIndex(event!!.point)
    if (index >= 0) {
      val changed = lastIndex != index
      if (changed) {
        lastIndex = index
        return "" // hide on-change to repaint on new position
      }
      return getTooltipItemText(model.getElementAt(index))
    }
    return ""
  }

  private fun getTooltipItemText(item: HistoryListItem): String {
    val durationSeconds =
        Duration.between(item.lastUpdated, LocalDateTime.now())
            .toKotlinDuration()
            .toString(DurationUnit.SECONDS)
    return "Last updated: $durationSeconds ago" // fixme "Last updated 843s ago"
  }
}
