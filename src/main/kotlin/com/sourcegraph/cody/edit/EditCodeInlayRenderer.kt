package com.sourcegraph.cody.edit

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.CellRendererPane
import javax.swing.JPanel

class EditCodeInlayRenderer(private val component: JPanel, private val requiredHeight: Int) :
    EditorCustomElementRenderer {

  private val rendererPane = CellRendererPane()

  init {
    // Call validate() initially to ensure the layout is updated
    rendererPane.add(component)
    component.validate()
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    // TODO: compute the width based on the line width or editor viewport width
    return maxOf(component.preferredSize.width, MAX_EDITOR_WIDTH)
  }

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return requiredHeight
  }

  override fun paint(
      inlay: Inlay<*>,
      g: Graphics,
      targetRegion: Rectangle,
      textAttributes: TextAttributes
  ) {
    // Various failed attempts to get something to render in the inlay area.
    rendererPane.bounds = targetRegion
    component.setBounds(0, 0, targetRegion.width, targetRegion.height)
    component.validate()
    rendererPane.validate()
    rendererPane.background = JBColor.WHITE
    component.background = JBColor.orange

    // Paint the component using the rendererPane
    rendererPane.paintComponent(
        g,
        component,
        null,
        targetRegion.x,
        targetRegion.y,
        targetRegion.width,
        targetRegion.height,
        true)
  }

  companion object {
    private const val MAX_EDITOR_WIDTH = 500
  }
}
