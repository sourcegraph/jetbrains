package com.sourcegraph.cody.agent.protocol

import com.intellij.openapi.editor.Editor
import junit.framework.TestCase

fun Editor.testing_selectSubstring(substring: String) {
  val index = this.document.text.indexOf(substring)
  if (index == -1) {
    TestCase.fail("editor does not include substring '$substring'\n${this.document.text}")
  }
  this.selectionModel.setSelection(index, index + substring.length)
  TestCase.assertEquals(this.selectionModel.selectedText, substring)
}

fun Editor.testing_offset(pos: Position): Int {
  val startLine = this.document.getLineStartOffset(pos.line)
  return startLine + pos.character
}

fun Editor.testing_substring(range: Range): String {
  return this.document.text.substring(
      this.testing_offset(range.start), this.testing_offset(range.end))
}