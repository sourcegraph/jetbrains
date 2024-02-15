package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.protocol.DisplayCodeLensParams
import com.sourcegraph.cody.agent.protocol.ProtocolCodeLens
import com.sourcegraph.cody.edit.widget.LensWidgetGroup

/**
 * This class handles displaying and dispatching code lenses. There should be at most one instance
 * of this class and one active session per editor.
 */
class InlineCodeLenses(val session: InlineFixupSession, val editor: Editor) : Disposable {
  private val logger = Logger.getInstance(InlineCodeLenses::class.java)

  private val widgetGroup = LensWidgetGroup(this, editor)
  var inlay: Inlay<EditorCustomElementRenderer>? = null
    private set

  private val ACTIONS =
      mapOf(
          "Show Diff" to { showDiff() },
          "Accept" to { accept() },
          "Retry" to { retry() },
          "Undo" to { undo() },
          "Cancel" to { cancel() })

  /**
   * Creates a fresh code-lens inlay associated with the given editor.
   *
   * @params - the RPC params for the code lenses to display.
   */
  @RequiresEdt
  fun display(params: DisplayCodeLensParams) {
   disposeInlay()
    try {
      widgetGroup.reset()
      widgetGroup.setActions(ACTIONS)
      widgetGroup.parse(params.codeLenses)
    } catch (x: Exception) {
      logger.error("Error building CodeLens widgets", x)
      return
    }
    val offset =
        editor.document.getLineStartOffset(getLineForDisplayingLenses(params.codeLenses.first()))
    inlay = editor.inlayModel.addBlockElement(offset, true, false, 0, widgetGroup)
  }

  fun update() {
    inlay?.update()
  }

  // Brings up a diff view showing the changes the AI made.
  private fun showDiff() {
    logger.warn("Code Lenses: Show Diff")
  }

  fun accept() {
    logger.warn("Code Lenses: Accept")
  }

  fun retry() {
    logger.warn("Code Lenses: Retry")
  }

  fun undo() {
    logger.warn("Code Lenses: Undo")
  }

  fun cancel() {
    session.cancelCurrentJob()
  }

  override fun dispose() {
    disposeInlay()
  }

  private fun disposeInlay() {
    if (inlay != null) {
      Disposer.dispose(inlay!!)
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
    private val codeLensesForEditor = mutableMapOf<Editor, InlineCodeLenses>()

    fun get(session: InlineFixupSession, editor: Editor): InlineCodeLenses {
      return codeLensesForEditor.computeIfAbsent(editor) { InlineCodeLenses(session, editor) }
    }
  }
}
