package com.sourcegraph.cody.edit.fixupActions

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.sourcegraph.cody.agent.protocol.TextEdit

// Handles deletion requests as well, which are just replacements with "".
class ReplaceUndoableAction(
    project: Project,
    edit: TextEdit, // Instructions for the replacement.
    document: Document
) : FixupUndoableAction(project, edit, document) {

  private val replacementText = edit.value ?: "" // "" for deletions
  private var beforeMarker = createBeforeMarker()
  private var originalText: String? = null
  private var afterMarker: RangeMarker? = null

  private constructor(
      other: ReplaceUndoableAction,
      document: Document
  ) : this(other.project, other.edit, document) {
    this.beforeMarker = other.beforeMarker?.let { createRangeMarker(it.startOffset, it.endOffset) }
    this.afterMarker = other.afterMarker?.let { createRangeMarker(it.startOffset, it.endOffset) }
    this.originalText = other.originalText
  }

  override fun apply() {
    val (start, end) =
        beforeMarker?.let { sortOffsets(it.startOffset, it.endOffset) }
            ?: sortOffsets(0, document.textLength)
    originalText = document.getText(TextRange(start, end))
    document.replaceString(start, end, replacementText)
    afterMarker = beforeMarker?.let { createRangeMarker(start, start + replacementText.length) }
  }

  override fun undo() {
    val (start, end) =
        afterMarker?.let { sortOffsets(it.startOffset, it.endOffset) }
            ?: Pair(0, document.textLength)
    document.replaceString(start, end, originalText!!)
  }

  override fun dispose() {
    beforeMarker?.dispose()
    afterMarker?.dispose()
  }

  override fun copyForDocument(doc: Document): ReplaceUndoableAction {
    return ReplaceUndoableAction(this, doc)
  }

  private fun createBeforeMarker(): RangeMarker? {
    val range = edit.range ?: return null
    if (range.start.line == -1 || range.end.line == -1) return null
    val startOffset = document.getLineStartOffset(range.start.line) + range.start.character
    val endOffset = document.getLineStartOffset(range.end.line) + range.end.character

    return createRangeMarker(startOffset, endOffset)
  }

  private fun createRangeMarker(offset1: Int, offset2: Int): RangeMarker {
    val (start, end) = sortOffsets(offset1, offset2)
    return document.createRangeMarker(start, end)
  }

  // We have seen start < end: https://github.com/sourcegraph/jetbrains/issues/1570
  private fun sortOffsets(offset1: Int, offset2: Int): Pair<Int, Int> {
    return if (offset1 > offset2) Pair(offset2, offset1) else Pair(offset1, offset2)
  }

  override fun toString(): String {
    return """${javaClass.name} for $edit
      beforeMarker: $beforeMarker
      afterMarker: $afterMarker
      originalText: $originalText
      replacementText: $replacementText
    """
        .trimIndent()
  }
}
