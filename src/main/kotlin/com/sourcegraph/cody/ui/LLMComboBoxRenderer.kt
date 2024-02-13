package com.sourcegraph.cody.ui

import com.intellij.ui.CellRendererPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

class LLMComboBoxRenderer(var isCurrentUserFree: Boolean) : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(
      list: JList<*>?,
      llmComboBoxItem: Any?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
  ): Component {
    super.getListCellRendererComponent(list, llmComboBoxItem, index, isSelected, cellHasFocus)
    return (llmComboBoxItem as? LLMComboBoxItem)?.let(::renderItem) ?: this
  }

  private fun renderItem(llmComboBoxItem: LLMComboBoxItem): CellRendererPanel {
    val panel = CellRendererPanel(BorderLayout())
    val textBadgePanel = JPanel(BorderLayout())

    textBadgePanel.add(JLabel(llmComboBoxItem.name), BorderLayout.CENTER)
    textBadgePanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0))
    textBadgePanel.background = this.background

    if (llmComboBoxItem.codyProOnly && isCurrentUserFree) {
      textBadgePanel.add(JLabel("<html><b>PRO</b></html>"), BorderLayout.EAST)
      //      textBadgePanel.add(JLabel(<ICON>), BorderLayout.EAST) // todo: badge icon
    }

    val iconLabel = JLabel(llmComboBoxItem.icon)
    panel.add(iconLabel, BorderLayout.WEST)
    panel.add(textBadgePanel, BorderLayout.CENTER)
    return panel
  }
}
