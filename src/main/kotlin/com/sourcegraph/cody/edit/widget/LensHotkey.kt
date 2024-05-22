package com.sourcegraph.cody.edit.widget

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D

class LensHotkey(group: LensWidgetGroup, private val text: String) : LensLabel(group, text) {

  private val hotkeyHighlightColor = JBColor.PanelBackground

  private val highlight = LabelHighlight(hotkeyHighlightColor)

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int {
    return fontMetrics.stringWidth(text) + 8
  }

  @Suppress("UseJBColor")
  override fun paint(g: Graphics2D, x: Float, y: Float) {
    // Resize font and get new metrics
    val originalFont = g.font
    val resizedFont = originalFont.deriveFont(Font.BOLD, originalFont.size * 0.8f)
    g.font = resizedFont
    val fontMetrics = g.fontMetrics

    // Calculate width and height with resized font
    val width = fontMetrics.stringWidth(text) + 10
    val height = fontMetrics.height + 3

    // Draw highlight
    highlight.drawHighlight(g, x + 2, y, width, height)

    // Draw the text
    g.color = JBColor.foreground()
    g.drawString(text, x + 7, y + fontMetrics.ascent + 1)

    // Restore original font
    g.font = originalFont
  }

  override fun toString(): String {
    return "LensHotkey(text=$text)"
  }
}
