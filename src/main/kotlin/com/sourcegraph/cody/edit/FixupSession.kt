package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.agent.protocol.Position
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.vscode.CancellationToken
import java.util.concurrent.atomic.AtomicReference

/**
 * Common functionality for commands that let the agent edit the code inline, such as adding a doc
 * string, or fixing up a region according to user instructions.
 */
abstract class FixupSession(val editor: Editor) : Disposable {
  protected val controller = FixupService.instance

  protected val currentJob = AtomicReference(CancellationToken())
  protected var taskId: String? = null

  var performedEdits = false
    private set

  abstract fun getLogger(): Logger

  fun finish() {
    Disposer.dispose(this)
  }

  override fun dispose() {
    cancelCurrentJob()
  }

  fun cancelCurrentJob() {
    if (!currentJob.get().isCancelled) {
      currentJob.get().abort()
      currentJob.set(CancellationToken())
    }
  }

  abstract fun accept()

  /** Subclasses must handle the retry operation. */
  abstract fun retry()

  /** Cancel this session and discard all resources. */
  abstract fun cancel()

  fun performInlineEdits(edits: List<TextEdit>) {
    if (!controller.isEligibleForInlineEdit(editor)) {
      return getLogger().warn("Inline edit not eligible")
    }
    WriteCommandAction.runWriteCommandAction(editor.project ?: return) {
      val doc: Document = editor.document
      val project = editor.project ?: return@runWriteCommandAction
      for (edit in edits) {
        // TODO: handle options if present (currently just undo bounds)
        when (edit.type) {
          "replace" -> performReplace(doc, edit)
          "insert" -> performInsert(doc, edit)
          "delete" -> performDelete(doc, edit)
          else -> getLogger().warn("Unknown edit type: ${edit.type}")
        }
        // TODO: Would be nice to group all the edits into a single undo action.
        UndoManager.getInstance(project)
                .undoableActionPerformed(FixupUndoableAction.from(editor, edit))
      }
    }
  }

  private fun performReplace(doc: Document, edit: TextEdit) {
    val (start, end) = edit.range?.toOffsets(doc) ?: return
    doc.replaceString(start, end, edit.value ?: return)

  }

  private fun performInsert(doc: Document, edit: TextEdit): TextEdit? {
    // TODO: Figure out why insertion-based selection guessing isn't being called in the agent.
    // For now, hack it to insert at beginning of line (BOL) at cursor, for demo purposes.
    val c = editor.caretModel.primaryCaret.offset
    val lineStart = doc.getLineStartOffset(doc.getLineNumber(c))
    val pos = Position.fromOffset(editor.document, lineStart)
    val insert = edit.value ?: return null
    // TODO: We shouldn't have to be fixing this up.
    val insertText = if (insert.endsWith("\n")) insert else "$insert\n"
    val textEdit = TextEdit("insert", null, pos, insertText)

    val start = textEdit.position?.toOffset(doc) ?: return null
    // Set this flag before we make the edit, since callbacks are called synchronously.
    performedEdits = true
    doc.insertString(start, insertText)
    return textEdit
  }

  private fun performDelete(doc: Document, edit: TextEdit) {
    val (start, end) = edit.range?.toOffsets(doc) ?: return
    performedEdits = true
    doc.deleteString(start, end)
  }

  protected fun undoEdits() {
    val project = editor.project ?: return
    if (project.isDisposed) return
    val fileEditor = getEditorForDocument(editor.document, project)
    val undoManager = UndoManager.getInstance(project)
    if (undoManager.isUndoAvailable(fileEditor)) {
      undoManager.undo(fileEditor)
    }
  }

  private fun getEditorForDocument(document: Document, project: Project): FileEditor? {
    val file = FileDocumentManager.getInstance().getFile(document)
    return file?.let { getCurrentFileEditor(project, it) }
  }

  private fun getCurrentFileEditor(project: Project, file: VirtualFile): FileEditor? {
    return FileEditorManager.getInstance(project).getEditors(file).firstOrNull()
  }
}
