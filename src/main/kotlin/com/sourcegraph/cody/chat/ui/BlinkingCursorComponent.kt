package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.Disposable
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import javax.swing.JPanel
import javax.swing.Timer

class BlinkingCursorComponent : JPanel(), Disposable {
  private var showCursor = true

  private val timer: Timer =
      Timer(500) {
        showCursor = !showCursor
        repaint()
      }

  init {
    timer.start()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (showCursor) {
      g.font = Font("Monospaced", Font.PLAIN, 12)
      g.drawString("█", 10, 20)
      g.color = UIUtil.getActiveTextColor()
      background = UIUtil.getPanelBackground()
    }
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(30, 30)
  }

  override fun dispose() {
    timer.stop()
  }
}
