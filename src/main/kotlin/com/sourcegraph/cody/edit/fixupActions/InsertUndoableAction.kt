package com.sourcegraph.cody.edit.fixupActions

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.protocol.TextEdit

class InsertUndoableAction(project: Project, edit: TextEdit, document: Document) :
    FixupUndoableAction(project, edit, document) {

  private val insertText = edit.value ?: ""
  private var beforeMarker = createBeforeMarker()
  private var afterMarker: RangeMarker? = null

  private constructor(
      other: InsertUndoableAction,
      document: Document
  ) : this(other.project, other.edit, document) {
    this.beforeMarker =
        if (other.beforeMarker == null) null
        else
            document.createRangeMarker(
                other.beforeMarker!!.startOffset, other.beforeMarker!!.endOffset)
    this.afterMarker =
        if (other.afterMarker == null) null
        else
            document.createRangeMarker(
                other.afterMarker!!.startOffset, other.afterMarker!!.endOffset)
  }

  private fun createBeforeMarker(): RangeMarker? {
    val position = edit.position ?: return null
    if (position.line < 0 || position.character < 0) return null
    val startOffset = document.getLineStartOffset(position.line) + position.character
    return document.createRangeMarker(startOffset, startOffset)
  }

  override fun apply() {
    if (isUndoInProgress() || beforeMarker == null) return
    document.insertString(beforeMarker!!.startOffset, insertText)
    afterMarker =
        document.createRangeMarker(
            beforeMarker!!.startOffset, beforeMarker!!.startOffset + insertText.length)
  }

  override fun undo() {
    if (isUndoInProgress() || afterMarker == null) return
    document.deleteString(afterMarker!!.startOffset, afterMarker!!.endOffset)
  }

  override fun dispose() {
    beforeMarker?.dispose()
    afterMarker?.dispose()
  }

  override fun copyForDocument(doc: Document): InsertUndoableAction {
    return InsertUndoableAction(this, doc)
  }

  override fun toString(): String {
    return """${javaClass.name} for $edit
      beforeMarker: $beforeMarker
      afterMarker: $afterMarker
      insertText: $insertText
    """
        .trimIndent()
  }
}
