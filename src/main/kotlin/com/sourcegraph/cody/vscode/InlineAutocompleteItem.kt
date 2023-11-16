package com.sourcegraph.cody.vscode

@JvmInline
value class CompletionItemID(val value: String)

class InlineAutocompleteItem(
    var insertText: String,
    val filterText: String,
    var range: Range,
    val command: Command?,
    val logId: CompletionItemID
)
