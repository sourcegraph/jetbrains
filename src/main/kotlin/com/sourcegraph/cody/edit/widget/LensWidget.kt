package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.Disposable
import java.awt.FontMetrics
import java.awt.Graphics2D

interface LensWidget : Disposable {
  fun calcWidthInPixels(fontMetrics: FontMetrics): Int

  fun calcHeightInPixels(fontMetrics: FontMetrics): Int

  fun paint(g: Graphics2D, x: Float, y: Float)

  // Optional method for updating the widget state, useful for animations.
  fun update() {
    // Default implementation does nothing
  }

  fun onClick(x: Float, y: Float): Boolean {
    return false
  }

  override fun dispose() {}
}
