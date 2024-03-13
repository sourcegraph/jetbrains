package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.Gray
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.agent.protocol.DisplayCodeLensParams
import com.sourcegraph.cody.agent.protocol.ProtocolCodeLens
import com.sourcegraph.cody.edit.FixupService.Companion.backgroundThread
import com.sourcegraph.cody.edit.FixupSession
import java.awt.*
import java.awt.geom.Rectangle2D
import java.util.concurrent.atomic.AtomicBoolean

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

  val isDisposed = AtomicBoolean(false)

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

  val widgetFont =
      with(editor.colorsScheme.getFont(EditorFontType.PLAIN)) { Font(name, style, size - 2) }

  // Compute inlay height based on the widget font, not the editor font.
  private val inlayHeight =
      FontInfo.getFontMetrics(
              Font(
                  editor.colorsScheme.fontPreferences.fontFamily,
                  widgetFont.style,
                  widgetFont.size),
              FontInfo.getFontRenderContext(editor.contentComponent))
          .height

  private var widgetFontMetrics: FontMetrics? = null

  private var lastHoveredWidget: LensWidget? = null // Used for mouse rollover highlighting.

  /**
   * This bears some explanation. The protocol doesn't tell us when the "last" lens is sent. This is
   * a heuristic; we flag what looks like the final lens as soon as we see it arrive.
   */
  private var receivedAcceptLens = false

  var inlay: Inlay<EditorCustomElementRenderer>? = null

  private var prevCursor: Cursor? = null

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

  fun display(params: DisplayCodeLensParams, callbacks: Map<String, () -> Unit>) {
    backgroundThread {
      try {
        commandCallbacks = callbacks
        parse(params.codeLenses)
      } catch (x: Exception) {
        logger.error("Error building CodeLens widgets", x)
        return@backgroundThread
      }
      val lens = params.codeLenses.first()
      val offset =
          editor.document.getLineStartOffset(
              if (!lens.range.isZero()) {
                (lens.range.start.line - 1).coerceAtLeast(0)
              } else {
                editor.caretModel.currentCaret.logicalPosition.line
              })
      ApplicationManager.getApplication().invokeLater {
        if (!isDisposed.get()) {
          inlay = editor.inlayModel.addBlockElement(offset, true, false, 0, this)
          Disposer.register(this, inlay!!)
        }
      }
    }
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
    return inlayHeight
  }

  private fun widgetGroupXY(): Point {
    return editor.offsetToXY(inlay?.offset ?: return Point(0, 0))
  }

  fun widgetXY(widget: LensWidget): Point {
    val ourXY = widgetGroupXY()
    val fontMetrics = widgetFontMetrics ?: editor.getFontMetrics(Font.PLAIN)
    var sum = 0
    for (w in widgets) {
      if (w == widget) break
      sum += w.calcWidthInPixels(fontMetrics)
    }
    return Point(ourXY.x + sum, ourXY.y)
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
      val title = lens.command?.title ?: return@forEachIndexed
      val command = lens.command.command
      if (command == "cody.fixup.codelens.accept") {
        receivedAcceptLens = true
      }
      // Add any icons sent along. Currently, always left-aligned and one at a time.
      title.icons?.forEach { icon ->
        when (icon.value) {
          SPINNER_MARKER -> {
            widgets.add(LensSpinner(this, Icons.StatusBar.CompletionInProgress))
            widgets.add(LensLabel(this, ICON_SPACER))
          }
          LOGO_MARKER -> {
            widgets.add(LensIcon(this, Icons.CodyLogo))
            widgets.add(LensLabel(this, ICON_SPACER))
          }
        }
      }
      // All remaining widget types have title text.
      val text = title.text?.trim()
      if (text == null) {
        logger.warn("Missing title text in CodeLens: $lens")
        return@forEachIndexed
      }
      val callback = commandCallbacks[command]
      if (callback == null) { // Label
        widgets.add(LensLabel(this, text))
        if (text.isNotEmpty()) separator = true
      } else if (command != null) { // Action
        // This is a hack, but works for the lenses we've seen so far.
        // We only start adding separators after the first action or nonempty label.
        if (i < lenses.size && separator) {
          widgets.add(LensLabel(this, SEPARATOR))
        }
        widgets.add(LensAction(this, text, command, callback))
        separator = true
      } else {
        logger.warn("Skipping malformed widget: $lens")
      }
    }
    widgets.forEach { Disposer.register(this, it) }
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
    val lastWidget = lastHoveredWidget

    if (widget is LensAction) {
      prevCursor = e.editor.contentComponent.cursor
      e.editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    } else {
      if (prevCursor != null) {
        e.editor.contentComponent.cursor = prevCursor!!
        prevCursor = null
      }
    }

    // Check if the mouse has moved from one widget to another or from/to outside
    if (widget != lastWidget) {
      lastWidget?.onMouseExit(e)
      lastHoveredWidget = widget // null if now outside
      widget?.onMouseEnter(e)
      inlay?.update() // force repaint
    }
  }

  private fun handleDocumentChanged(@Suppress("UNUSED_PARAMETER") e: DocumentEvent) {
    // We let them edit up until we show the Accept lens, and then editing auto-accepts.
    if (receivedAcceptLens) {
      //session.accept()
    }
  }

  /** Immediately hides and discards this inlay and widget group. */
  override fun dispose() {
    isDisposed.set(true)
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
