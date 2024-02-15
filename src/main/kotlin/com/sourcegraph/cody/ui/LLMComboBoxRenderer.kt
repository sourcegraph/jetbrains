package com.sourcegraph.cody.ui

import com.intellij.openapi.util.IconLoader.toImage
import com.intellij.ui.CellRendererPanel
import com.sourcegraph.cody.Icons
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

class LLMComboBoxRenderer : DefaultListCellRenderer() {

  private var isCurrentUserFree: Boolean = true

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
    textBadgePanel.border = (BorderFactory.createEmptyBorder(0, 5, 0, 0))
    textBadgePanel.background = this.background

    if (llmComboBoxItem.codyProOnly && isCurrentUserFree) {
      val badge = ImageIcon(toImage(Icons.LLM.ProSticker).getScaledInstance(20, 20, Image.SCALE_SMOOTH))
      textBadgePanel.add(JLabel(badge), BorderLayout.EAST)
    }

    val iconLabel = JLabel(llmComboBoxItem.icon)
    panel.add(iconLabel, BorderLayout.WEST)
    panel.add(textBadgePanel, BorderLayout.CENTER)
    return panel
  }

  fun updateTier(isCurrentUserFree: Boolean) {
    this.isCurrentUserFree = isCurrentUserFree
  }
}
