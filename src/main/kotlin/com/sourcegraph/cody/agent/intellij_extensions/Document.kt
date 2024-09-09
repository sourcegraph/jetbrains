package com.sourcegraph.cody.agent.intellij_extensions

import com.intellij.openapi.editor.Document
import com.sourcegraph.cody.agent.protocol_extensions.Position
import com.sourcegraph.cody.agent.protocol_generated.Position
import com.sourcegraph.cody.agent.protocol_generated.Range
import kotlin.math.min

fun Document.codyPosition(offset: Int): Position {
  val line = this.getLineNumber(offset)
  val lineStartOffset = this.getLineStartOffset(line)
  val character = offset - lineStartOffset
  return Position(line, character)
}

fun Document.codyRange(startOffset: Int, endOffset: Int): Range {
  val startLine = this.getLineNumber(min(startOffset, this.textLength))
  val lineStartOffset1 = this.getLineStartOffset(startLine)
  val startCharacter = startOffset - lineStartOffset1

  val endLine = this.getLineNumber(endOffset)
  val lineStartOffset2 =
      if (startLine == endLine) {
        lineStartOffset1
      } else {
        this.getLineStartOffset(endLine)
      }
  val endCharacter = endOffset - lineStartOffset2

  return Range(Position(startLine, startCharacter), Position(endLine, endCharacter))
}
