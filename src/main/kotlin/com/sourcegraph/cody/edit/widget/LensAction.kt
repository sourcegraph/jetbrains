package com.sourcegraph.cody.edit.widget

import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.font.TextAttribute
import java.awt.geom.Rectangle2D
import java.util.*

class LensAction(private val text: String, private val onClick: () -> Unit) : LensWidget {

  // Bounds of the last paint call, to check for clicks
  private var lastPaintedBounds: Rectangle2D.Float? = null

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int = fontMetrics.stringWidth(text)

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    val originalFont = g.font
    try {
      g.font =
          originalFont.deriveFont(
              Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON))
      g.drawString(text, x, y + g.fontMetrics.ascent)
    } finally {
      g.font = originalFont
    }
    // After drawing, update lastPaintedBounds with the area we just used.
    val metrics = g.fontMetrics
    val width = metrics.stringWidth(text)
    val height = metrics.height
    lastPaintedBounds = Rectangle2D.Float(x, y - metrics.ascent, width.toFloat(), height.toFloat())
  }

  override fun onClick(x: Float, y: Float): Boolean {
    onClick.invoke()
    return true
  }

  override fun toString(): String {
    return "LensAction(text=$text)"
  }
}
