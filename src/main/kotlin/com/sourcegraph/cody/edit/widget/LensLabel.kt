package com.sourcegraph.cody.edit.widget

import com.sourcegraph.cody.edit.EditCommandPrompt
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics2D
import javax.swing.UIManager

class LensLabel(
    group: LensWidgetGroup,
    private val text: String,
    private val isHotkey: Boolean = false
) : LensWidget(group) {

  private val hotkeyColor = Color(49, 51, 56)

  private val highlight =
      LabelHighlight(if (isHotkey) hotkeyColor else EditCommandPrompt.textFieldBackground())

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int = fontMetrics.stringWidth(text)

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    if (isHotkey) {
      val textWidth = g.fontMetrics.stringWidth(text)
      val textHeight = g.fontMetrics.height
      highlight.drawHighlight(g, x, y, textWidth, textHeight)
    }
    g.color =
        when {
          isHotkey -> EditCommandPrompt.subduedLabelColor()
          text == LensGroupFactory.SEPARATOR -> UIManager.getColor("TextField.background")
          else -> EditCommandPrompt.boldLabelColor()
        }
    g.drawString(text, x, y + g.fontMetrics.ascent)
  }

  override fun toString(): String {
    return "LensLabel(text=$text)"
  }
}
