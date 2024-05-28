package com.sourcegraph.cody.agent.protocol

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import kotlin.math.max
import kotlin.math.min

data class Position(@JvmField val line: Long, @JvmField val character: Long) {
  constructor(line: Int, character: Int) : this(line.toLong(), character.toLong())

  fun isStartOrEndOfDocumentMarker(document: Document): Boolean {
    return line < 0 || line > document.lineCount
  }

  fun getLogicalLine(document: Document): Int {
    return min(max(0L, document.lineCount.toLong() - 1), line).toInt()
  }

  fun getLogicalColumn(document: Document): Int {
    val logicalLine = getLogicalLine(document)
    val lineLength =
        document.getLineEndOffset(logicalLine) - document.getLineStartOffset(logicalLine)
    return min(lineLength.toLong(), character).toInt()
  }

  fun toLogicalPosition(document: Document): LogicalPosition {
    return LogicalPosition(getLogicalLine(document), getLogicalColumn(document))
  }

  /** Return zero-based offset of this position in the document. */
  fun toOffset(document: Document): Int {
    val lineStartOffset = document.getLineStartOffset(getLogicalLine(document))
    return lineStartOffset + getLogicalColumn(document)
  }

  companion object {
    fun fromOffset(document: Document, offset: Int): Position {
      val line = document.getLineNumber(offset)
      val lineStartOffset = document.getLineStartOffset(line)
      return Position(line.toLong(), (offset - lineStartOffset).toLong())
    }
  }
}
