package com.sourcegraph.cody.edit

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.agent.protocol.TextDocumentEditParams
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.vscode.CancellationToken

/**
 * Common functionality for commands that let the agent edit the code inline,
 * such as adding a doc string, or fixing up a region according to user instructions.
 */
abstract class InlineFixupCommandSession(val editor: Editor, val cancellationToken: CancellationToken) {
  protected val controller = InlineFixups.instance

  protected var taskId: String? = null

  abstract fun cancel()

  abstract fun getLogger(): Logger

  fun performInlineEdits(params: TextDocumentEditParams) {
    if (!controller.isEligibleForInlineEdit(editor)) {
      getLogger().warn("Inline edit not eligible")
      return
    }
    WriteCommandAction.runWriteCommandAction(editor.project ?: return) {
      for (edit in params.edits) {
        val doc: Document = editor.document
        // TODO: handle options if present (currently just undo bounds)
        when (edit.type) {
          "replace" -> performReplace(doc, edit)
          "insert" -> performInsert(doc, edit)
          "delete" -> performDelete(doc, edit)
          else -> getLogger().warn("Unknown edit type: ${edit.type}")
        }
      }
    }
    // TODO: Fix up selection.
    editor.caretModel.primaryCaret.removeSelection()
  }

  private fun performReplace(doc: Document, edit: TextEdit) {
    val (start, end) = edit.range?.toOffsets(doc) ?: return
    doc.replaceString(start, end, edit.value ?: return)
  }

  private fun performInsert(doc: Document, edit: TextEdit) {
    val start = edit.range?.start?.toOffset(doc) ?: return
    doc.insertString(start, edit.value ?: return)
  }

  private fun performDelete(doc: Document, edit: TextEdit) {
    val (start, end) = edit.range?.toOffsets(doc) ?: return
    doc.deleteString(start, end)
  }

}
