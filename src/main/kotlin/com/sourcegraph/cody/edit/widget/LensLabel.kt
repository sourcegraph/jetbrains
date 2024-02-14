package com.sourcegraph.cody.edit.widget

import java.awt.FontMetrics
import java.awt.Graphics2D

class LensLabel(private val text: String) : LensWidget {
  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int = fontMetrics.stringWidth(text)

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    g.drawString(text, x, y + g.fontMetrics.ascent)
  }

  override fun onClick(x: Float, y: Float): Boolean {
    // Labels do nothing when clicked.
    return true
  }

  override fun toString(): String {
    return "LensLabel(text=$text)"
  }
}
