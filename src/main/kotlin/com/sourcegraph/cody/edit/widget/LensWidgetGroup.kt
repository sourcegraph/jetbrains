package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.Gray
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.agent.protocol.ProtocolCodeLens
import com.sourcegraph.cody.edit.InlineCodeLenses
import java.awt.*
import java.awt.geom.Rectangle2D

/**
 * Manages a simple row of [LensWidget]s, delegating to them for rendering, managing their
 * positioning, and routing mouse events.
 */
class LensWidgetGroup(val controller: InlineCodeLenses, parentComponent: Editor) :
    EditorCustomElementRenderer, Disposable {
  private val logger = Logger.getInstance(LensWidgetGroup::class.java)
  val editor = parentComponent as EditorImpl

  val widgets = mutableListOf<LensWidget>()

  lateinit var supportedActions: Map<String, () -> Unit>

  lateinit var mouseClickListener: EditorMouseListener
  lateinit var mouseMotionListener: EditorMouseMotionListener

  lateinit var widgetFont: Font
  var widgetFontMetrics: FontMetrics? = null

  private var lastHoveredWidget: LensWidget? = null

  operator fun Point.component1() = this.x

  operator fun Point.component2() = this.y

  init {
    registerMouseHandlers()
    initWidgetFont()
  }

  val inlay: Inlay<EditorCustomElementRenderer>? by controller::inlay

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
    g.color = lensColor
    if (widgetFontMetrics == null) { // Cache for hit box detection later.
      widgetFontMetrics = g.fontMetrics
    }
    val top = targetRegion.y.toFloat()
    // Draw all the widgets left to right, keeping track of their width.
    widgets.fold(targetRegion.x.toFloat()) { acc, widget ->
      try {
        widget.paint(g, acc, top)
        acc + widget.calcWidthInPixels(g.fontMetrics)
      } finally {
        g.font = widgetFont // In case widget changed it.
      }
    }
  }

  fun update() {
    controller.update()
  }

  private fun initWidgetFont() {
    val editorFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
    widgetFont = Font(editorFont.name, editorFont.style, editorFont.size - 2)
  }

  private fun findWidgetAt(x: Int, y: Int): LensWidget? {
    var currentX = 0f // Widgets are left-aligned in the editor.
    val fontMetrics = widgetFontMetrics ?: return null
    // Make sure it's in our bounds.
    if (inlay?.bounds?.contains(x, y) == false) return null
    // Walk widgets left to right checking their hit boxes.
    for (widget in widgets) {
      val widgetWidth = widget.calcWidthInPixels(fontMetrics)
      val rightEdgeX = currentX + widgetWidth
      if (x >= currentX && x <= rightEdgeX) { // In widget's bounds?
        return widget
      }
      currentX = rightEdgeX
      // Add to currentX here to increase spacing.
    }
    return null
  }

  override fun dispose() {
    editor.removeEditorMouseListener(mouseClickListener)
    editor.removeEditorMouseMotionListener(mouseMotionListener)
    widgets.clear() // Don't hold refs to disposed objects
  }

  /* Parse the RPC data and create widgets. */
  fun parse(lenses: List<ProtocolCodeLens>) {
    var separator = false
    lenses.forEachIndexed { i, lens ->
      // Even icons/spinners are currently encoded in the title field.
      var text = (lens.command?.title ?: return@forEachIndexed).trim()

      // These two cases are encoded in the title field.
      // TODO: Protocol should split them into separate lenses.
      // "$(sync~spin) ..."
      if (text.startsWith(spinnerMarker)) {
        widgets.add(LensSpinner(this, Icons.StatusBar.CompletionInProgress))
        widgets.add(LensLabel(this, iconSpacer))
        text = text.removePrefix(spinnerMarker).trimStart()
      }
      // "$(cody-logo) ..."
      if (text.startsWith(logoMarker)) {
        widgets.add(LensIcon(this, Icons.CodyLogo))
        widgets.add(LensLabel(this, iconSpacer))
        text = text.removePrefix(logoMarker).trimStart()
      }
      // All that's left are actions and labels.
      if (text in supportedActions) {
        // This is a hack, but works for the lenses we've seen so far.
        // We only start adding separators after the first action or nonempty label.
        if (i < lenses.size && separator) {
          widgets.add(LensLabel(this, actionSeparator))
        }
        widgets.add(LensAction(this, text, supportedActions[text]!!))
        separator = true
      } else {
        widgets.add(LensLabel(this, text))
        if (text.isNotEmpty()) separator = true
      }
    }
  }

  private fun registerMouseHandlers() {
    // TODO: Register all these with Disposable
    mouseClickListener =
        object : EditorMouseListener {
          override fun mouseClicked(e: EditorMouseEvent) {
            val (x, y) = e.mouseEvent.point
            if (findWidgetAt(x, y)?.onClick(x, y) == true) {
              e.consume()
            }
          }
        }
    editor.addEditorMouseListener(mouseClickListener)
    mouseMotionListener =
        object : EditorMouseMotionListener {
          override fun mouseMoved(e: EditorMouseEvent) {
            val (x, y) = e.mouseEvent.point
            val widget = findWidgetAt(x, y)
            val lastWidget = lastHoveredWidget
            // Check if the mouse has moved from one widget to another or from/to outside
            if (widget != lastWidget) {
              lastWidget?.onMouseExit()
              // Update the lastHoveredWidget (can be null if now outside)
              lastHoveredWidget = widget
              widget?.onMouseEnter()
            }
          }
        }
    editor.addEditorMouseMotionListener(mouseMotionListener)
  }

  companion object {
    const val logoMarker = "$(cody-logo)"
    const val spinnerMarker = "$(sync~spin)"
    const val iconSpacer = " "
    const val actionSeparator = " | "
    val lensColor = Gray._153
  }
}
