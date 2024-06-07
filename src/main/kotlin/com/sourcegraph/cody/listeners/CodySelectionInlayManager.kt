import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.Disposer
import com.sourcegraph.config.ThemeUtil
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.util.*

@Suppress("UseJBColor")
class CodySelectionInlayManager {

  private var currentInlay: Inlay<*>? = null
  private var inlayBounds: Rectangle? = null

  private val disposable = Disposer.newDisposable()

  private fun updateInlay(editor: Editor, content: String, line: Int, above: Boolean) {
    clearInlay()

    val inlayModel: InlayModel = editor.inlayModel
    val adjustedLine = if (above && line > 0) line - 1 else line
    val offset =
        if (above) editor.document.getLineEndOffset(adjustedLine)
        else editor.document.getLineEndOffset(line)

    val inlay =
        inlayModel.addInlineElement(
            offset,
            object : EditorCustomElementRenderer {
              override fun calcWidthInPixels(inlay: Inlay<*>): Int {
                val fontMetrics =
                    inlay.editor.contentComponent.getFontMetrics(
                        inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN))
                return fontMetrics.stringWidth(content)
              }

              override fun paint(
                  inlay: Inlay<*>,
                  g: Graphics,
                  targetRegion: Rectangle,
                  textAttributes: TextAttributes
              ) {
                val font = inlay.editor.colorsScheme.getFont(EditorFontType.PLAIN)
                val smallerFont = Font(font.name, font.style, font.size - 2)
                    g.font = smallerFont

                val caretRowColor = editor.colorsScheme.getColor(EditorColors.CARET_ROW_COLOR)
                val backgroundColor =
                    if (isCaretOnSameLine(editor, inlay)) {
                      caretRowColor ?: editor.colorsScheme.defaultBackground
                    } else {
                      editor.colorsScheme.defaultBackground
                    }
                g.color = backgroundColor
                g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)

                val descent = g.fontMetrics.descent

                val textColor =
                    if (ThemeUtil.isDarkTheme()) Color(0x74, 0x75, 0x76)
                    else Color(0x64, 0x6C, 0x72)

                g.color = textColor

                val baseline = targetRegion.y + targetRegion.height - descent - 2

                g.drawString(content, targetRegion.x, baseline)
                inlayBounds = targetRegion
              }
            })

    inlay?.let {
      Disposer.register(disposable, it)
      currentInlay = it
    }
  }

  private fun isCaretOnSameLine(editor: Editor, inlay: Inlay<*>): Boolean {
    val caretModel = editor.caretModel
    val caretOffset = caretModel.offset
    val caretLine = editor.document.getLineNumber(caretOffset)
    val inlayLine = editor.document.getLineNumber(inlay.offset)
    return caretLine == inlayLine
  }

  private fun clearInlay() {
    currentInlay?.let { Disposer.dispose(it) }
    currentInlay = null
    inlayBounds = null
  }

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
    val selectionStartLine = if (startOffset < endOffset) startLine else endLine

    val caretOffset = editor.caretModel.offset
    val drawAbove = caretOffset == startOffset

    val editShortcutText = getKeyStrokeText("cody.editCodeAction")
    val chatShortcutText = getKeyStrokeText("cody.newChat")
    val inlayContent = " $editShortcutText to Edit "

    val lineToDraw = if (drawAbove) selectionStartLine else selectionEndLine

    updateInlay(editor, inlayContent, lineToDraw, drawAbove)
  }

  fun dispose() {
    Disposer.dispose(disposable)
  }
}
