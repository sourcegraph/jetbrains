package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.sourcegraph.cody.agent.protocol.DisplayCodeLensParams
import com.sourcegraph.cody.agent.protocol.ProtocolCodeLens
import com.sourcegraph.cody.autocomplete.render.AutocompleteRenderUtil
import java.awt.Font
import java.awt.Graphics2D
import java.awt.font.TextAttribute
import java.awt.geom.Rectangle2D
import java.util.*

/** This class handles displaying and dispatching code lens events. */
class InlineFixupCodeLenses(val editor: Editor) {
  private val logger = Logger.getInstance(InlineFixupCodeLenses::class.java)

  private val inlays: MutableSet<Inlay<InlineFixupCodeLensRenderer>> = mutableSetOf()

  fun display(params: DisplayCodeLensParams) {
    clearInlays()
    params.codeLenses.forEach { lens ->
      editor.inlayModel
          .addBlockElement(
              editor.document.getLineStartOffset(getLineForDisplayingLenses(lens)),
              InlayProperties(),
              InlineFixupCodeLensRenderer(lens))
          ?.let { inlays.add(it) }
    }
  }

  fun clearInlays() {
    if (!editor.isDisposed) {
      inlays.forEach { Disposer.dispose(it) }
    }
    inlays.clear()
  }

  private inner class InlineFixupCodeLensRenderer(val lens: ProtocolCodeLens) :
      EditorCustomElementRenderer {

    private val title = lens.command?.title ?: ""

    override fun calcWidthInPixels(p0: Inlay<*>): Int {
      val editor = editor as EditorImpl
      return editor.getFontMetrics(Font.PLAIN).stringWidth(title)
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics2D,
        targetRegion: Rectangle2D,
        textAttributes: TextAttributes
    ) {
      super.paint(inlay, g, targetRegion, textAttributes) // validates params
      g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
      g.color = AutocompleteRenderUtil.getTextAttributesForEditor(editor).foregroundColor
      val x = targetRegion.x
      val y = (targetRegion.y + AutocompleteRenderUtil.fontYOffset(g.font, editor))
      renderCodeLens(g, x.toFloat(), y.toFloat())
    }

    private fun renderCodeLens(g: Graphics2D, x: Float, y: Float) {
      // 3 cases:
      //  - special syntax for an icon or non-text indicator
      //  - title that by convention has a well-defined operation, e.g. Undo, Cancel
      //  - fall back to the title as-is
      //  TODO: this protocol design will have issues when we localize the code lenses.
      //    - also it needs more structure rather than the client guessing things
      val text = lens.command?.title ?: "<unknown lens>"
      if (text in arrayOf("Show Diff", "Accept", "Retry", "Undo")) {
        g.font =
            g.font.deriveFont(
                Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON))
        g.drawString(text, x, y)
      } else {
        g.drawString(text, x, y)
      }
    }
  }

  private fun getLineForDisplayingLenses(lens: ProtocolCodeLens): Int {
    if (!lens.range.isZero()) {
      return lens.range.start.line
    }
    // Zeroed out (first char in buffer) usually means it's uninitialized/invalid.
    // We recompute it just to be sure.
    val line = editor.caretModel.logicalPosition.line
    // TODO: Fallback should be the caret when the command was invoked.
    return (line - 1).coerceAtLeast(0) // Fall back to line before caret.
  }

  companion object {
    val LOGO = "$(cody-logo)"
    val SPINNER = "$(sync~spin)"
  }
}
