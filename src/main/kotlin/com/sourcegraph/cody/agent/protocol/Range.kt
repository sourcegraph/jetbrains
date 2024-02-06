package com.sourcegraph.cody.agent.protocol

import com.intellij.openapi.editor.Document

data class Range(val start: Position, val end: Position) {

  fun toOffsets(document: Document): Pair<Int, Int> {
    return Pair(start.toOffset(document), end.toOffset(document))
  }
}
