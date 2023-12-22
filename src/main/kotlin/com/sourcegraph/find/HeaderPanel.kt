package com.sourcegraph.find

import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.components.BorderLayoutPanel
import com.sourcegraph.Icons
import com.sourcegraph.config.GoToPluginSettingsButtonFactory.createGoToPluginSettingsButton
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class HeaderPanel : BorderLayoutPanel() {
  init {
    this.setBorder(JBEmptyBorder(5, 5, 2, 5))
    val title =
        JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
          setBorder(JBEmptyBorder(2, 0, 0, 0))
          add(JLabel("Find with Sourcegraph", Icons.SourcegraphLogo, SwingConstants.LEFT))
        }
    val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
    buttons.add(createGoToPluginSettingsButton())
    this.add(title, BorderLayout.WEST)
    this.add(buttons, BorderLayout.EAST)
  }
}
