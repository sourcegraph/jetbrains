package com.sourcegraph.cody.ui

import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class LLMModelComboBoxRenderer : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(
      list: JList<*>?,
      value: Any?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
  ): Component {
    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    val llmModelComboBoxItem: LLMModelComboBoxItem? = value as? LLMModelComboBoxItem
    llmModelComboBoxItem?.let(::renderItem)
    return this
  }

  private fun renderItem(llmModelComboBoxItem: LLMModelComboBoxItem) {
    if (llmModelComboBoxItem.codyProOnly) {
      setText("${llmModelComboBoxItem.name} \t PRO")
    } else {
      setText(llmModelComboBoxItem.name)
    }
    setIcon(llmModelComboBoxItem.icon)
  }
}
