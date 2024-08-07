package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.protocol_extensions.toLogicalPosition
import com.sourcegraph.cody.agent.protocol_extensions.toOffset
import com.sourcegraph.cody.agent.protocol_generated.Range
import java.awt.Cursor
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.math.roundToInt
import org.jetbrains.annotations.NotNull

operator fun Point.component1() = this.x

operator fun Point.component2() = this.y

/**
 * Manages a single code lens group. It should only be displayed once, and disposed after displaying
 * it, before displaying another.
 */
class LensWidgetGroup(parentComponent: Editor) : EditorCustomElementRenderer, Disposable {
  private val logger = Logger.getInstance(LensWidgetGroup::class.java)
  val editor = parentComponent as EditorImpl

  val isDisposed = AtomicBoolean(false)
  private val addedListeners = AtomicBoolean(false)
  private val removedListeners = AtomicBoolean(false)

  val widgets = mutableListOf<LensWidget>()

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

  private var listenersMuted = false

  val widgetFont = AtomicReference(UIUtil.getLabelFont())

  // Compute inlay height based on the widget font, not the editor font.
  private val inlayHeight = AtomicReference(computeInlayHeight())

  private var widgetFontMetrics: FontMetrics? = null

  private var lastHoveredWidget: LensWidget? = null // Used for mouse rollover highlighting.

  var inlay: Inlay<EditorCustomElementRenderer>? = null

  private var lastComputedIndent = RECOMPUTE

  init {
    editor.addEditorMouseListener(mouseClickListener)
    editor.addEditorMouseMotionListener(mouseMotionListener)
    addedListeners.set(true)

    // Listen for color theme changes.
    ApplicationManager.getApplication()
        .messageBus
        .connect(this)
        .subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener {
              updateFonts()
              update()
            })
  }

  private fun updateFonts() {
    val font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
    widgetFont.set(Font(font.name, font.style, font.size))
    widgetFontMetrics = null // force recalculation
  }

  @RequiresEdt
  fun show(range: Range, shouldScrollToLens: Boolean) {
    val offset = range.start.toOffset(editor.document)
    if (isDisposed.get()) {
      throw IllegalStateException("Request to show disposed inlay: $this")
    }
    inlay = editor.inlayModel.addBlockElement(offset, false, true, 0, this)
    Disposer.register(this, inlay!!)

    if (shouldScrollToLens) {
      val logicalPosition = range.start.toLogicalPosition(editor.document)
      editor.scrollingModel.scrollTo(logicalPosition, ScrollType.CENTER)
    }
  }

  // Propagate repaint requests from widgets to the inlay.
  fun update() {
    inlayHeight.set(computeInlayHeight())
    inlay?.apply {
      update()
      repaint()
    }
  }

  private fun computeInlayHeight(): Int {
    val font =
        Font(
            editor.colorsScheme.fontPreferences.fontFamily,
            widgetFont.get().style,
            widgetFont.get().size)
    val fontMetrics =
        FontInfo.getFontMetrics(font, FontInfo.getFontRenderContext(editor.contentComponent))

    val totalHeight = fontMetrics.ascent + fontMetrics.descent

    return (totalHeight * INLAY_HEIGHT_SCALE_FACTOR).roundToInt()
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return editor.component.width
  }

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    return inlayHeight.get()
  }

  private fun widgetGroupXY(): Point {
    return editor.offsetToXY(inlay?.offset ?: return Point(0, 0))
  }

  fun widgetXY(widget: LensWidget): Point {
    val ourXY = widgetGroupXY()
    val fontMetrics = widgetFontMetrics ?: editor.getFontMetrics(Font.PLAIN)
    var sum = leftMargin()
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
    g.font = widgetFont.get()
    if (widgetFontMetrics == null) { // Cache for hit box detection later.
      widgetFontMetrics = g.fontMetrics
    }

    val inlayHeight = calcHeightInPixels(inlay)

    // Draw the inlay background across the width of the Editor.
    g.color = JBColor(0xECEDF2, 0x26282D)
    g.fillRect(
        targetRegion.x.roundToInt(),
        targetRegion.y.roundToInt(),
        calcWidthInPixels(inlay),
        inlayHeight)

    val fontMetrics = g.fontMetrics
    val fontHeight = fontMetrics.ascent + fontMetrics.descent
    val verticalPadding = (inlayHeight - fontHeight) / 2

    val top = targetRegion.y + verticalPadding
    val left = targetRegion.x + leftMargin()

    // Draw all the widgets left to right, keeping track of their x-position.
    widgets.fold(left) { acc, widget ->
      try {
        widget.paint(g, acc.toFloat(), top.toFloat())
        acc + widget.calcWidthInPixels(g.fontMetrics)
      } finally {
        g.font = widgetFont.get()
      }
    }
  }

  private fun findWidgetAt(x: Int, y: Int): LensWidget? {
    var currentX = leftMargin()
    val fontMetrics = widgetFontMetrics ?: return null
    if (inlay?.bounds?.contains(x, y) == false) return null

    // Walk widgets left to right checking their hit boxes.
    for (widget in widgets) {
      val widgetWidth = widget.calcWidthInPixels(fontMetrics)
      val rightEdgeX = currentX + widgetWidth
      if (x >= currentX && x <= rightEdgeX) { // In widget's bounds?
        return widget
      }
      currentX = rightEdgeX
    }
    return null
  }

  fun addWidget(widget: LensWidget) {
    widgets.add(widget)
  }

  fun registerWidgets() {
    widgets.forEach { Disposer.register(this, it) }
  }

  // Computes the X coordinate in the Editor where the first widget is drawn.
  private fun leftMargin(): Int {
    if (lastComputedIndent != RECOMPUTE) {
      return lastComputedIndent
    }

    try {
      val document = editor.document
      val inlayOffset = inlay?.offset
      if (inlayOffset == null) {
        lastComputedIndent = DEFAULT_MARGIN
        return DEFAULT_MARGIN
      }

      val lineCount = document.lineCount
      val inlayLineNumber = document.getLineNumber(inlayOffset)

      // Find next non-blank line.
      for (lineNumber in inlayLineNumber until lineCount) {
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))

        // Compute the pixel width of the indentation.
        if (lineText.isNotBlank()) {
          val tabSize = EditorUtil.getTabSize(editor)
          val spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, editor)
          val indentationLevel =
              lineText
                  .takeWhile { it.isWhitespace() }
                  .sumOf { if (it == '\t') tabSize * spaceWidth else spaceWidth }

          lastComputedIndent = indentationLevel
          return indentationLevel
        }
      }
    } catch (x: Exception) {
      logger.warn("Error computing code lens left margin", x)
    }
    lastComputedIndent = DEFAULT_MARGIN
    return DEFAULT_MARGIN
  }

  fun reset() {
    lastComputedIndent = RECOMPUTE
  }

  // Dispatch mouse click events to the appropriate widget.
  private fun handleMouseClick(e: EditorMouseEvent) {
    val (x, y) = e.mouseEvent.point
    if (findWidgetAt(x, y)?.onClick(e) == true) {
      e.consume()
    }
  }

  private fun handleMouseMove(e: EditorMouseEvent) {
    val (x, y) = e.mouseEvent.point
    val widget = findWidgetAt(x, y)
    val lastWidget = lastHoveredWidget

    if (widget is LensAction) {
      e.editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    } else {
      e.editor.contentComponent.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    }

    // Check if the mouse has moved from one widget to another or from/to outside.
    if (widget != lastWidget) {
      lastWidget?.onMouseExit(e)
      lastHoveredWidget = widget // null if now outside
      widget?.onMouseEnter(e)
      inlay?.update() // force repaint
    }
  }

  /** Immediately hides and discards this inlay and widget group. */
  override fun dispose() {
    // We work extra hard to ensure this method is idempotent and robust,
    // because IntelliJ (annoyingly) logs an assertion if you try to remove
    // a nonexistent listener, and it pops up a user-visible exception.
    if (isDisposed.get()) return
    isDisposed.set(true)
    if (editor.isDisposed) return
    onEventThread {
      if (editor.isDisposed) return@onEventThread
      if (addedListeners.get() && !removedListeners.get()) {
        try {
          removedListeners.set(true)
          editor.removeEditorMouseListener(mouseClickListener)
          editor.removeEditorMouseMotionListener(mouseMotionListener)
        } catch (t: Throwable) {
          logger.warn("Error removing mouse listeners", t)
        }
      }
      try {
        disposeInlay()
      } catch (t: Throwable) {
        logger.warn("Error disposing inlay", t)
      }
    }
  }

  @RequiresEdt
  private fun disposeInlay() {
    inlay?.apply {
      if (isValid) {
        Disposer.dispose(this)
      }
      inlay = null
    }
  }

  private fun <T> onEventThread(handler: Supplier<T>): @NotNull CompletableFuture<T> {
    val result = CompletableFuture<T>()
    val executeAndComplete: () -> Unit = {
      try {
        result.complete(handler.get())
      } catch (e: Exception) {
        result.completeExceptionally(e)
      }
    }
    if (ApplicationManager.getApplication().isDispatchThread) {
      executeAndComplete()
    } else {
      runInEdt { executeAndComplete() }
    }
    return result
  }

  override fun toString(): String {
    val render = widgets.joinToString(separator = ",") { it.toString() }
    return "LensWidgetGroup: {$render}"
  }

  companion object {
    private const val DEFAULT_MARGIN = 20

    // The height of the inlay is always scaled to the font height,
    // with room for the buttons and some top/bottom padding. This setting
    // was found empirically and seems to work well for all font sizes.
    private const val INLAY_HEIGHT_SCALE_FACTOR = 1.2

    // Flag to force recomputation of left margin.
    private const val RECOMPUTE = -1
  }
}
