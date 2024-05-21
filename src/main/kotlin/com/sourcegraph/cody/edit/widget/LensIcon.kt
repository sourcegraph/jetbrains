package com.sourcegraph.cody.edit.widget

import java.awt.FontMetrics
import java.awt.Graphics2D
import javax.swing.Icon

class LensIcon(group: LensWidgetGroup, val icon: Icon) : LensWidget(group) {

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int {
    // Calculate the desired width based on the font height, adjusted by a factor
    val desiredHeight = (fontMetrics.height + fontMetrics.ascent) / 2.0f
    val scaleFactor = desiredHeight / icon.iconHeight.toFloat()
    return (icon.iconWidth * scaleFactor).toInt()
  }

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int {
    // Calculate the desired height based on the font height, adjusted by a factor
    return ((fontMetrics.height + fontMetrics.ascent) / 2.0f).toInt()
  }

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    val fontMetrics = g.fontMetrics
    val textCenterLine = y + (fontMetrics.ascent + fontMetrics.descent) / 2.0f
    val desiredHeight = (fontMetrics.height + fontMetrics.ascent) / 2.0f
    val scaleFactor = desiredHeight / icon.iconHeight.toFloat()
    val iconY = textCenterLine - desiredHeight / 2.0f

    // Apply scaling transformation
    val originalTransform = g.transform
    g.translate(x.toInt(), iconY.toInt())
    g.scale(scaleFactor.toDouble(), scaleFactor.toDouble())

    // Paint the icon
    icon.paintIcon(null, g, 0, 0)

    // Restore original transformation
    g.transform = originalTransform
  }

  override fun toString(): String {
    return "LensIcon(icon=$icon)"
  }
}
