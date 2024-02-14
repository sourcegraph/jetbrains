package com.sourcegraph.cody.edit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.vscode.CancellationToken

/**
 * Common functionality for commands that let the agent edit the code inline, such as adding a doc
 * string, or fixing up a region according to user instructions.
 */
abstract class InlineFixupCommandSession(
    val editor: Editor,
    val cancellationToken: CancellationToken
) {
  private val logger = Logger.getInstance(InlineFixupCommandSession::class.java)
  protected val controller = InlineFixups.instance

  protected var taskId: String? = null

  abstract fun cancel()

  abstract fun getLogger(): Logger

  fun performInlineEdits(edits: List<TextEdit>) {
    if (!controller.isEligibleForInlineEdit(editor)) {
      getLogger().warn("Inline edit not eligible")
      return
    }
    WriteCommandAction.runWriteCommandAction(editor.project ?: return) {
      for (edit in edits) {
        val doc: Document = editor.document
        // TODO: handle options if present (currently just undo bounds)
        when (edit.type) {
          "replace" -> performReplace(doc, edit)
          "insert" -> performInsert(doc, edit)
          "delete" -> performDelete(doc, edit)
          else -> getLogger().warn("Unknown edit type: ${edit.type}")
        }
      }
      ApplicationManager.getApplication().invokeLater {
        // TODO: Fix up selection.
        editor.caretModel.primaryCaret.removeSelection()
      }
    }
  }

  private fun getOffsets(doc: Document, edit: TextEdit): Pair<Int, Int>? {
    if (edit.range == null) {
      logger.warn("Invalid edit range: ${edit.range} for edit: ${edit.type}")
      return null
    }
    return edit.range.toOffsets(doc)
  }

  private fun performReplace(doc: Document, edit: TextEdit) {
    val (start, end) = getOffsets(doc, edit) ?: return
    doc.replaceString(start, end, edit.value ?: return)
  }

  private fun performInsert(doc: Document, edit: TextEdit) {
    val (start, _) = getOffsets(doc, edit) ?: return
    doc.insertString(start, edit.value ?: return)
  }

  private fun performDelete(doc: Document, edit: TextEdit) {
    val (start, end) = getOffsets(doc, edit) ?: return
    doc.deleteString(start, end)
  }
}
