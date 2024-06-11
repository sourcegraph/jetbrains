package com.sourcegraph.cody.listeners

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.geom.GeneralPath
import java.util.*

class CodySelectionInlayManager {

  private var currentInlay: Inlay<*>? = null

  private val disposable = Disposer.newDisposable()

  private fun updateInlay(editor: Editor, content: String, line: Int) {
    clearInlay()

    val inlay =
      editor.inlayModel.addInlineElement(
        editor.document.getLineEndOffset(line),
        object : EditorCustomElementRenderer {
          override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            val font = UIUtil.getLabelFont()
            val smallerFont = Font(font.name, font.style, font.size - 2)
            val fontMetrics = inlay.editor.contentComponent.getFontMetrics(smallerFont)
            return fontMetrics.stringWidth(content)
          }

          override fun paint(
            inlay: Inlay<*>,
            g: Graphics,
            targetRegion: Rectangle,
            textAttributes: TextAttributes
          ) {
            val font = UIUtil.getLabelFont()
            val smallerFont = Font(font.name, font.style.or(Font.BOLD), font.size - 2)
            g.font = smallerFont

            val backgroundColor =
              editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR)?.darker()
            g.color = backgroundColor

            val arcSize = 10

            val path = GeneralPath()

            // Start at top-left
            path.moveTo(targetRegion.x.toDouble(), targetRegion.y.toDouble())

            // Top edge
            path.lineTo(
              (targetRegion.x + targetRegion.width).toDouble(), targetRegion.y.toDouble()
            )

            // Right edge
            path.lineTo(
              (targetRegion.x + targetRegion.width).toDouble(),
              (targetRegion.y + targetRegion.height - arcSize).toDouble()
            )
            path.quadTo(
              (targetRegion.x + targetRegion.width).toDouble(),
              (targetRegion.y + targetRegion.height).toDouble(),
              (targetRegion.x + targetRegion.width - arcSize).toDouble(),
              (targetRegion.y + targetRegion.height).toDouble()
            )

            // Bottom edge
            path.lineTo(
              (targetRegion.x + arcSize).toDouble(),
              (targetRegion.y + targetRegion.height).toDouble()
            )
            path.lineTo(
              targetRegion.x.toDouble(), (targetRegion.y + targetRegion.height).toDouble()
            )

            // Left edge
            path.lineTo(targetRegion.x.toDouble(), targetRegion.y.toDouble())

            path.closePath()
            (g as Graphics2D).fill(path)

            val descent = g.fontMetrics.descent
            val textColor = editor.colorsScheme.getColor(EditorColors.CARET_COLOR)
            g.color = textColor

            val baseline = targetRegion.y + targetRegion.height - descent - 4
            g.drawString(content, targetRegion.x, baseline)
          }
        })

    inlay?.let {
      Disposer.register(disposable, it)
      currentInlay = it
    }
  }

  private fun clearInlay() {
    currentInlay?.let { Disposer.dispose(it) }
    currentInlay = null
  }

  @Suppress("SameParameterValue")
  private fun getKeyStrokeText(actionId: String): String {
    val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)
    if (shortcuts.isNotEmpty()) {
      val firstShortcut = shortcuts[0]
      if (firstShortcut is KeyboardShortcut) {
        val keyStroke = firstShortcut.firstKeyStroke
        val modifiers = keyStroke.modifiers
        val key = KeyEvent.getKeyText(keyStroke.keyCode)
        val isMac = System.getProperty("os.name").lowercase(Locale.getDefault()).contains("mac")
        val separator = " + "

        val modText = buildString {
          append(" ") // Add some separation from the end of the source code.
          if (modifiers and KeyEvent.CTRL_DOWN_MASK != 0) append("Ctrl$separator")
          if (modifiers and KeyEvent.SHIFT_DOWN_MASK != 0) append("Shift$separator")
          if (modifiers and KeyEvent.ALT_DOWN_MASK != 0) append("Alt$separator")
          if (isMac && modifiers and KeyEvent.META_DOWN_MASK != 0) append("Cmd$separator")
        }
        return (modText + key).removeSuffix(separator)
      }
    }
    return ""
  }

  fun handleSelectionChanged(editor: Editor, event: SelectionEvent) {
    val startOffset = event.newRange.startOffset
    val endOffset = event.newRange.endOffset
    if (startOffset == endOffset) {
      // Don't show if there's no selection
      clearInlay()
      return
    }
    val startLine = editor.document.getLineNumber(startOffset)
    val endLine = editor.document.getLineNumber(endOffset)
    val selectionEndLine = if (startOffset > endOffset) startLine else endLine

    val editShortcutText = getKeyStrokeText("cody.editCodeAction")
    val inlayContent = " $editShortcutText  to Edit    "

    updateInlay(editor, inlayContent, selectionEndLine)
  }

  fun dispose() {
    Disposer.dispose(disposable)
  }
}
