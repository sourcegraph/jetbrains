package com.sourcegraph.cody.ui

import java.awt.*
import javax.swing.JButton

class TransparentButton(text: String, private val textColor: Color) : JButton(text) {
  private val opacity = 0.7f
  private val cornerRadius = 5
  private val horizontalPadding = 15
  private val verticalPadding = 5

  init {
    isContentAreaFilled = false
    isFocusPainted = false
    isBorderPainted = false

    // Calculate the preferred size based on the size of the text
    val fm = getFontMetrics(font)
    val width = fm.stringWidth(getText()) + horizontalPadding * 2
    val height = fm.height + verticalPadding * 2
    preferredSize = Dimension(width, height)
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    ui.update(g2, this)
    g2.composite = AlphaComposite.SrcOver.derive(opacity)
    g2.color = background
    g2.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
    g2.color = foreground
    g2.stroke = BasicStroke(1f)
    g2.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
    g2.dispose()
    g.color = textColor
  }
}
