package com.sourcegraph.cody.ui

import com.intellij.ide.ui.UISettings
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.JButton

class TransparentButton(text: String) : JButton(text) {
  private val opacity = 0.7f
  private val cornerRadius = 5
  private val horizontalPadding = 10
  private val verticalPadding = 5
  private val textColor = UIUtil.getLabelForeground()

  init {
    isContentAreaFilled = false
    isFocusPainted = false
    isBorderPainted = false
    isVisible = false

    // Calculate the preferred size based on the size of the text
    val fm = getFontMetrics(font)
    val width = fm.stringWidth(getText()) + horizontalPadding * 2
    val height = fm.height + verticalPadding * 2
    preferredSize = Dimension(width, height)
  }

  override fun paintComponent(g: Graphics) {
    UISettings.setupAntialiasing(g)
    val g2 = g.create() as Graphics2D
    g2.composite = AlphaComposite.SrcOver.derive(opacity)
    g2.color = background
    g2.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
    g2.color = foreground
    g2.stroke = BasicStroke(1f)
    g2.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
    g2.dispose()
    g.color = textColor
    val fm = g.fontMetrics
    val rect = fm.getStringBounds(text, g)
    val textHeight = rect.height.toInt()
    val textWidth = rect.width.toInt()

    // Center text horizontally and vertically
    val x = (width - textWidth) / 2
    val y = (height - textHeight) / 2 + fm.ascent
    g.drawString(text, x, y)
  }
}
