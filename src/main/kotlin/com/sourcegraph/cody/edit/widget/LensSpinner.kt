package com.sourcegraph.cody.edit.widget

import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.util.*
import javax.swing.Icon
import javax.swing.Timer

class LensSpinner(private val icon: Icon) : LensWidget {
  private var rotationDegrees = 0f
  private val animationDelay = 100 // Milliseconds between frames
  private lateinit var parentInvalidateCallback: () -> Unit

  private val timer =
      Timer(animationDelay) { e ->
        rotationDegrees =
            (rotationDegrees + 10) % 360 // Adjust rotation speed and step as necessary
        parentInvalidateCallback.invoke()
      }

  fun start(parentInvalidate: () -> Unit) {
    this.parentInvalidateCallback = parentInvalidate
    timer.start()
  }

  fun stop() {
    timer.stop()
  }

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int = icon.iconWidth

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = icon.iconHeight

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    val originalTransform = g.transform
    val iconCenterX = x + icon.iconWidth / 2
    val iconCenterY = y + icon.iconHeight / 2
    val transform =
        AffineTransform.getRotateInstance(
            Math.toRadians(rotationDegrees.toDouble()),
            iconCenterX.toDouble(),
            iconCenterY.toDouble())
    g.transform(transform)
    icon.paintIcon(null, g, x.toInt(), y.toInt())
    g.transform = originalTransform
  }

  override fun dispose() {
    stop()
  }

  override fun toString(): String {
    return "LensSpinner(rotationDegrees=$rotationDegrees, animationDelay=$animationDelay)"
  }
}
