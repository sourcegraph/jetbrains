package com.sourcegraph.cody.edit.widget

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.font.TextAttribute
import java.awt.geom.Rectangle2D
import java.util.*

class LensAction(
    group: LensWidgetGroup,
    private val text: String,
    private val onClick: () -> Unit
) : LensWidget(group) {

  // Bounds of the last paint call, to check for clicks
  private var lastPaintedBounds: Rectangle2D.Float? = null

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int = fontMetrics.stringWidth(text)

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    val originalFont = g.font
    val originalColor = g.color
    try {
      if (mouseInBounds) {
        val attributes = mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON)
        g.font = originalFont.deriveFont(attributes)
      } else {
        g.font = originalFont.deriveFont(Font.PLAIN)
      }
      g.color = if (mouseInBounds) JBColor.WHITE else JBColor.GRAY
      g.drawString(text, x, y + g.fontMetrics.ascent)

      // After drawing, update lastPaintedBounds with the area we just used.
      val metrics = g.fontMetrics
      val width = metrics.stringWidth(text)
      val height = metrics.height
      lastPaintedBounds =
          Rectangle2D.Float(x, y - metrics.ascent, width.toFloat(), height.toFloat())
    } finally {
      g.font = originalFont
      g.color = originalColor
    }
  }

  override fun onClick(x: Int, y: Int): Boolean {
    onClick.invoke()
    return true
  }

  override fun toString(): String {
    return "LensAction(text=$text)"
  }
}
