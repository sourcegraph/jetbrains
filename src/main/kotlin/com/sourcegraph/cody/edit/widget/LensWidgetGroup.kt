package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.agent.protocol.ProtocolCodeLens
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Manages a simple row of [LensWidget]s, delegating to them for rendering, managing their
 * positioning, and routing mouse events.
 */
class LensWidgetGroup(parent: Editor) : EditorCustomElementRenderer, Disposable {

  val editor = parent as EditorImpl

  val widgets = mutableListOf<LensWidget>()

  lateinit var supportedActions: Map<String, () -> Unit>

  lateinit var mouseHandler: EditorMouseListener

  lateinit var widgetFont: Font
  var widgetFontMetrics: FontMetrics? = null

  init {
    registerMouseHandler()
    initWidgetFont()
  }

  fun reset() {
    widgets.clear()
  }

  fun setActions(actions: Map<String, () -> Unit>) {
    supportedActions = actions
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    val fontMetrics = widgetFontMetrics ?: editor.getFontMetrics(Font.PLAIN)
    return widgets.sumOf { it.calcWidthInPixels(fontMetrics) } +
        (widgets.size - 1) * fontMetrics.stringWidth(" | ")
  }

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return editor.getFontMetrics(Font.PLAIN).height
  }

  override fun paint(
      inlay: Inlay<*>,
      g: Graphics2D,
      targetRegion: Rectangle2D,
      textAttributes: TextAttributes
  ) {
    g.font = widgetFont
    if (widgetFontMetrics == null) { // Cache for hit box detection later.
      widgetFontMetrics = g.fontMetrics
    }
    // Draw all the widgets left to right, keeping track of their width.
    widgets.fold(targetRegion.x.toFloat()) { acc, widget ->
      try {
        widget.paint(g, acc, targetRegion.y.toFloat())
        acc + widget.calcWidthInPixels(g.fontMetrics)
      } finally {
        g.font = widgetFont // In case widget changed it.
      }
    }
  }

  private fun initWidgetFont() {
    val editorFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
    val originalSize = editorFont.size2D
    widgetFont = Font("Arial, Helvetica, sans-serif", editorFont.style, (originalSize - 1).toInt())
  }

  // Dispatch click in our bounds to the LensWidget that was clicked.
  private fun handleClick(x: Int, y: Int): Boolean {
    var currentX = 0f // Widgets are left-aligned in the editor.
    val fontMetrics = widgetFontMetrics ?: return false
    // Walk widgets left to right checking their hit boxes.
    for (widget in widgets) {
      val widgetWidth = widget.calcWidthInPixels(fontMetrics)
      val rightEdgeX = currentX + widgetWidth
      if (x >= currentX && x <= rightEdgeX) { // In widget's bounds?
        if (widget.onClick(x - currentX, y.toFloat())) {
          return true
        }
      }
      currentX = rightEdgeX
      // Add to currentX here to increase spacing.
    }
    return false
  }

  override fun dispose() {
    widgets.forEach { it.dispose() }
    widgets.clear()
    supportedActions = emptyMap()
    editor.removeEditorMouseListener(mouseHandler)
  }

  /* Parse the RPC data and create widgets. */
  fun parse(lenses: List<ProtocolCodeLens>) {
    widgets.clear()
    var separator = false
    lenses.forEachIndexed { i, lens ->
      // Even icons/spinners are currently encoded in the title field.
      var text = (lens.command?.title ?: return@forEachIndexed).trim()

      // These two cases are encoded in the title field.
      // TODO: Protocol should split them into separate lenses.
      // "$(sync~spin) ..."
      if (text.startsWith(spinnerMarker)) {
        widgets.add(LensSpinner(Icons.StatusBar.CompletionInProgress))
        widgets.add(LensLabel(iconSpacer))
        text = text.removePrefix(spinnerMarker).trimStart()
      }
      // "$(cody-logo) ..."
      if (text.startsWith(logoMarker)) {
        widgets.add(LensIcon(Icons.CodyLogo))
        widgets.add(LensLabel(iconSpacer))
        text = text.removePrefix(logoMarker).trimStart()
      }
      // All that's left are actions and labels.
      if (text in supportedActions) {
        // This is a hack, but works for the lenses we've seen so far.
        // We only start adding separators after the first action or nonempty label.
        if (i < lenses.size && separator) {
          widgets.add(LensLabel(" | "))
        }
        widgets.add(LensAction(text, supportedActions[text]!!))
        separator = true
      } else {
        widgets.add(LensLabel(text))
        if (text.isNotEmpty()) separator = true
      }
    }
  }

  private fun registerMouseHandler() {
    mouseHandler =
        object : EditorMouseListener {
          override fun mouseClicked(e: EditorMouseEvent) {
            val point = e.mouseEvent.point
            if (handleClick(point.x, point.y)) {
              e.consume()
            }
          }
        }
    editor.addEditorMouseListener(mouseHandler)
  }

  companion object {
    val logoMarker = "$(cody-logo)"
    val spinnerMarker = "$(sync~spin)"
    val iconSpacer = "  "
  }
}
