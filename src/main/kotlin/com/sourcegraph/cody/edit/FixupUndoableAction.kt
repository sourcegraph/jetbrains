package com.sourcegraph.cody.edit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.sourcegraph.cody.agent.protocol.TextEdit

abstract class FixupUndoableAction(val editor: Editor, val edit: TextEdit) : UndoableAction {
  private val logger = Logger.getInstance(FixupUndoableAction::class.java)

  val document: Document = editor.document

  override fun getAffectedDocuments(): Array<out DocumentReference> {
    val documentReference = DocumentReferenceManager.getInstance().create(document)
    return arrayOf(documentReference)
  }

  override fun isGlobal(): Boolean {
    return true
  }

  // TODO: Use this.
  fun createRangeMarker(start: Int, end: Int): RangeMarker {
    val rangeMarker = document.createRangeMarker(start, end)
    rangeMarker.isGreedyToLeft = true
    rangeMarker.isGreedyToRight = true
    return rangeMarker
  }

  class InsertUndoableAction(editor: Editor, edit: TextEdit) : FixupUndoableAction(editor, edit) {

    override fun undo() {
      if (UndoManager.getInstance(editor.project ?: return).isUndoInProgress) return
      val offsets = (edit.range ?: return).toOffsets(document)
      ApplicationManager.getApplication().runWriteAction {
        document.deleteString(offsets.first, offsets.second)
      }
    }

    override fun redo() {
      if (UndoManager.getInstance(editor.project ?: return).isUndoInProgress) return
      val offset = edit.position?.toOffset(document) ?: return
      ApplicationManager.getApplication().runWriteAction {
        document.insertString(offset, edit.value ?: "")
      }
    }
  }

  class ReplaceUndoableAction(editor: Editor, edit: TextEdit) : FixupUndoableAction(editor, edit) {

    override fun undo() {
      if (UndoManager.getInstance(editor.project ?: return).isUndoInProgress) return

      TODO("fix")
    }

    override fun redo() {
      if (UndoManager.getInstance(editor.project ?: return).isUndoInProgress) return

      TODO("finish")
    }
  }

  class DeleteUndoableAction(editor: Editor, edit: TextEdit) : FixupUndoableAction(editor, edit) {
    var oldText = ""

    override fun undo() {
      if (UndoManager.getInstance(editor.project ?: return).isUndoInProgress) return

      val offsets = (edit.range ?: return).toOffsets(document)
      val deleted = document.text.substring(offsets.first, offsets.second)
      ApplicationManager.getApplication().runWriteAction {
        document.replaceString(offsets.first, offsets.second, oldText)
      }
      oldText = deleted
    }

    override fun redo() {
      if (UndoManager.getInstance(editor.project ?: return).isUndoInProgress) return

      val offsets = (edit.range ?: return).toOffsets(document)
      ApplicationManager.getApplication().runWriteAction {
        document.deleteString(offsets.first, offsets.second)
      }
      oldText = ""
    }
  }

  class NoOpUndoableAction(editor: Editor, edit: TextEdit) : FixupUndoableAction(editor, edit) {
    private val logger = Logger.getInstance(NoOpUndoableAction::class.java)

    override fun undo() {
      logger.warn("Received invalid undoable action for $edit")
    }

    override fun redo() {
      logger.warn("Received invalid undoable action for $edit")
    }
  }

  companion object {

    fun from(editor: Editor, edit: TextEdit): FixupUndoableAction {
      return when (edit.type) {
        "insert" -> InsertUndoableAction(editor, edit)
        "replace" -> ReplaceUndoableAction(editor, edit)
        "delete" -> DeleteUndoableAction(editor, edit)
        else -> NoOpUndoableAction(editor, edit)
      }
    }
  }
}
