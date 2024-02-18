package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.Gray
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.agent.protocol.DisplayCodeLensParams
import com.sourcegraph.cody.agent.protocol.ProtocolCodeLens
import com.sourcegraph.cody.edit.FixupSession
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

operator fun Point.component1() = this.x

operator fun Point.component2() = this.y

/**
 * Manages a single code lens group. It should only be displayed once, and disposed after displaying
 * it, before displaying another.
 */
class LensWidgetGroup(val session: FixupSession, parentComponent: Editor) :
    EditorCustomElementRenderer, Disposable {
  private val logger = Logger.getInstance(LensWidgetGroup::class.java)
  val editor = parentComponent as EditorImpl

  private val widgets = mutableListOf<LensWidget>()

  private lateinit var commandCallbacks: Map<String, () -> Unit>

  private val mouseClickListener =
      object : EditorMouseListener {
        override fun mouseClicked(e: EditorMouseEvent) {
          if (!listenersMuted) {
            handleMouseClick(e)
          }
        }
      }
  private val mouseMotionListener =
      object : EditorMouseMotionListener {
        override fun mouseMoved(e: EditorMouseEvent) {
          if (!listenersMuted) {
            handleMouseMove(e)
          }
        }
      }

  private val documentListener =
      object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          if (!listenersMuted) {
            handleDocumentChanged(event)
          }
        }
      }

  private var listenersMuted = false

  private val widgetFont =
      with(editor.colorsScheme.getFont(EditorFontType.PLAIN)) { Font(name, style, size - 2) }

  private var widgetFontMetrics: FontMetrics? = null

  private var lastHoveredWidget: LensWidget? = null

  var inlay: Inlay<EditorCustomElementRenderer>? = null

  init {
    Disposer.register(session, this)
    editor.addEditorMouseListener(mouseClickListener)
    editor.addEditorMouseMotionListener(mouseMotionListener)
    editor.document.addDocumentListener(documentListener)
  }

  fun withListenersMuted(block: () -> Unit) {
    try {
      listenersMuted = true
      block()
    } finally {
      listenersMuted = false
    }
  }

  @RequiresEdt
  fun display(params: DisplayCodeLensParams, callbacks: Map<String, () -> Unit>) {
    try {
      commandCallbacks = callbacks
      parse(params.codeLenses)
    } catch (x: Exception) {
      logger.error("Error building CodeLens widgets", x)
      return
    }
    val offset = editor.document.getLineStartOffset(getInlayLine(params.codeLenses.first()))
    inlay = editor.inlayModel.addBlockElement(offset, true, false, 0, this)
    Disposer.register(this, inlay!!)
  }

  // Propagate repaint requests from widgets to the inlay.
  fun update() {
    inlay?.update()
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    // We create widgets for everything including separators; sum their widths.
    // N.B. This method is never called; I suspect the inlay takes the whole line.
    val fontMetrics = widgetFontMetrics ?: editor.getFontMetrics(Font.PLAIN)
    return widgets.sumOf { it.calcWidthInPixels(fontMetrics) }
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

  private fun getInlayLine(lens: ProtocolCodeLens): Int {
    if (!lens.range.isZero()) {
      return lens.range.start.line
    }
    // Zeroed out (first char in buffer) usually means it's uninitialized/invalid.
    // We recompute it just to be sure.
    val line = editor.caretModel.logicalPosition.line
    // TODO: Fallback should be the caret when the command was invoked.
    return (line - 1).coerceAtLeast(0) // Fall back to line before caret.
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

  /* Parse the RPC data and create widgets. */
  private fun parse(lenses: List<ProtocolCodeLens>) {
    var separator = false
    lenses.forEachIndexed { i, lens ->
      // Even icons/spinners are currently encoded in the title field.
      var text = (lens.command?.title ?: return@forEachIndexed).trim()
      val command = lens.command.command

      // These two cases are encoded in the title field.
      // TODO: Protocol should split them into separate lenses.
      // "$(sync~spin) ..."
      if (text.startsWith(SPINNER_MARKER)) {
        widgets.add(LensSpinner(this, Icons.StatusBar.CompletionInProgress))
        widgets.add(LensLabel(this, ICON_SPACER))
        text = text.removePrefix(SPINNER_MARKER).trimStart()
      }
      // "$(cody-logo) ..."
      if (text.startsWith(LOGO_MARKER)) {
        widgets.add(LensIcon(this, Icons.CodyLogo))
        widgets.add(LensLabel(this, ICON_SPACER))
        text = text.removePrefix(LOGO_MARKER).trimStart()
      }
      // All that's left are actions and labels.
      if (command in commandCallbacks) {
        // This is a hack, but works for the lenses we've seen so far.
        // We only start adding separators after the first action or nonempty label.
        if (i < lenses.size && separator) {
          widgets.add(LensLabel(this, SEPARATOR))
        }
        widgets.add(LensAction(this, text, commandCallbacks[command]!!))
        separator = true
      } else {
        widgets.add(LensLabel(this, text))
        if (text.isNotEmpty()) separator = true
      }
    }
  }

  // Dispatch mouse click events to the appropriate widget.
  private fun handleMouseClick(e: EditorMouseEvent) {
    val (x, y) = e.mouseEvent.point
    if (findWidgetAt(x, y)?.onClick(x, y) == true) {
      e.consume()
    }
  }

  private fun handleMouseMove(e: EditorMouseEvent) {
    val (x, y) = e.mouseEvent.point
    val widget = findWidgetAt(x, y)
    // TODO: Fix hit box detection.
    //logger.debug("$x, $y -> $widget (last: $lastHoveredWidget)")
    val lastWidget = lastHoveredWidget
    // Check if the mouse has moved from one widget to another or from/to outside
    if (widget != lastWidget) {
      lastWidget?.onMouseExit()
      lastHoveredWidget = widget // null if now outside
      widget?.onMouseEnter()
    }
  }

  private fun handleDocumentChanged(@Suppress("UNUSED_PARAMETER") e: DocumentEvent) {
    session.cancel()
  }

  /** Immediately hides and discards this inlay and widget group. */
  override fun dispose() {
    if (editor.isDisposed) return
    editor.removeEditorMouseListener(mouseClickListener)
    editor.removeEditorMouseMotionListener(mouseMotionListener)
    editor.document.removeDocumentListener(documentListener)
    disposeInlay()
  }

  private fun disposeInlay() {
    inlay?.apply {
      if (isValid) {
        Disposer.dispose(this)
      }
      inlay = null
    }
  }

  companion object {
    const val LOGO_MARKER = "$(cody-logo)"
    const val SPINNER_MARKER = "$(sync~spin)"
    const val ICON_SPACER = " "
    const val SEPARATOR = " | "
    private val lensColor = Gray._150
  }
}
