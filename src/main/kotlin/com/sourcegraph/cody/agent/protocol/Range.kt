package com.sourcegraph.cody.agent.protocol

import com.intellij.openapi.editor.Document

typealias RangePair = Pair<Int, Int>

data class Range(val start: Position, val end: Position) {

  // We need to .plus(1) since the ranges use 0-based indexing
  // but IntelliJ presents it as 1-based indexing.
  fun intellijRange(): RangePair = RangePair(start.line.plus(1), end.line.plus(1))

  // The link to Sourcegraph Search on the other hand looks like this:
  fun toSearchRange(): RangePair = RangePair(start.line.plus(1), end.line)

  fun length() = end.line - start.line + 1

  fun toOffsets(document: Document): Pair<Int, Int> {
    return Pair(start.toOffset(document), end.toOffset(document))
  }
}
