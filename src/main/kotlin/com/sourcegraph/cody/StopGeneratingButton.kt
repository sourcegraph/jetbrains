package com.sourcegraph.cody

import com.intellij.icons.AllIcons
import com.intellij.util.IconUtil
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

class StopGeneratingButton :
    JButton("Stop generating", IconUtil.desaturate(AllIcons.Actions.Suspend)) {
  val wrappingPanel =
      JPanel(FlowLayout(FlowLayout.CENTER, 0, 5)).also {
        it.preferredSize =
            Dimension(
                Short.MAX_VALUE.toInt(), this@StopGeneratingButton.getPreferredSize().height + 10)
      }

  init {
    isVisible = false
    wrappingPanel.add(this)
    wrappingPanel.isOpaque = false
  }
}
