package com.sourcegraph.cody.agent.protocol

import com.intellij.openapi.editor.Document

data class Range(val start: Position, val end: Position) {

  fun toOffsets(document: Document): Pair<Int, Int> {
    return Pair(start.toOffset(document), end.toOffset(document))
  }

  // Return true if the agent gave us all zeroes for the range.
  fun isZero() = start.isZero() && end.isZero()
}
