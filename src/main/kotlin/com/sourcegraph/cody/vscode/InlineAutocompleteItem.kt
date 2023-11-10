package com.sourcegraph.cody.vscode

class InlineAutocompleteItem(
    var insertText: String,
    val filterText: String,
    var range: Range,
    val command: Command
) {
  override fun toString(): String {
    return "InlineAutocompleteItem(insertText='$insertText', filterText='$filterText', range=$range, command=$command)"
  }
}
