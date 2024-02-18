package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.awt.FontMetrics
import java.awt.Graphics2D

abstract class LensWidget(val parentGroup: LensWidgetGroup) : Disposable {
  protected val logger = Logger.getInstance(LensWidget::class.java)
  protected var mouseInBounds = false

  init {
    Disposer.register(parentGroup, this)
  }

  abstract fun calcWidthInPixels(fontMetrics: FontMetrics): Int

  abstract fun calcHeightInPixels(fontMetrics: FontMetrics): Int

  abstract fun paint(g: Graphics2D, x: Float, y: Float)

  /**
   * Optional method for updating the widget state, useful for animations.
   */
  open fun update() {
    // Default implementation does nothing
  }

  /**
   * Called only when widget is clicked.
   * Coordinates are relative to the widget.
   */
  open fun onClick(x: Int, y: Int): Boolean {
    return false
  }

  open fun onMouseEnter() {
    mouseInBounds = true
  }
  open fun onMouseExit() {
    mouseInBounds = false
  }

  override fun dispose() {}
}
