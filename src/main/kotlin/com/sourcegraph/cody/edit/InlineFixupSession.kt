package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.agent.protocol.Position
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.vscode.CancellationToken
import java.util.concurrent.atomic.AtomicReference

/**
 * Common functionality for commands that let the agent edit the code inline, such as adding a doc
 * string, or fixing up a region according to user instructions.
 */
abstract class InlineFixupSession(val editor: Editor) : Disposable {
  protected val controller = InlineFixups.instance

  protected val currentJob = AtomicReference(CancellationToken())
  protected var taskId: String? = null

  abstract fun getLogger(): Logger

  fun cancelCurrentJob() {
    getLogger().warn("Aborting current job: $this")
    currentJob.get().abort()
    currentJob.set(CancellationToken())
    // TODO:
    //  - make sure it's plumbed through to the agent
    //  - clear all the UI
  }

  fun performInlineEdits(edits: List<TextEdit>) {
    if (!controller.isEligibleForInlineEdit(editor)) {
      return getLogger().warn("Inline edit not eligible")
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
    if (edit.position != null) {
      val offset = edit.position.toOffset(doc)
      return Pair(offset, offset)
    }
    if (edit.range == null) {
      getLogger().warn("Invalid edit range: ${edit.range} for edit: ${edit.type}")
      return null
    }
    return edit.range.toOffsets(doc)
  }

  private fun performReplace(doc: Document, edit: TextEdit) {
    val (start, end) = getOffsets(doc, edit) ?: return
    doc.replaceString(start, end, edit.value ?: return)
  }

  private fun performInsert(doc: Document, edit: TextEdit) {
    // We're getting back zeroes right now for edit.position (and range).
    // TODO: Fix the Agent to compute the correct selection if none is passed.

    // For now, hack it to insert at beginning of line (BOL) at cursor, for demo purposes.
    val c = editor.caretModel.primaryCaret.offset
    val lineStart = doc.getLineStartOffset(doc.getLineNumber(c))
    val pos = Position.fromOffset(editor.document, lineStart)
    val insert = edit.value ?: return
    val insertText = if (insert.endsWith("\n")) insert else "$insert\n"
    val textEdit = TextEdit("insert", null, pos, insertText)

    val (start, _) = getOffsets(doc, textEdit) ?: return
    doc.insertString(start, insertText)
  }

  private fun performDelete(doc: Document, edit: TextEdit) {
    val (start, end) = getOffsets(doc, edit) ?: return
    doc.deleteString(start, end)
  }
}
