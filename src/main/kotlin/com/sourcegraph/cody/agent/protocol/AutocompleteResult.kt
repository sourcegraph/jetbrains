package com.sourcegraph.cody.agent.protocol

import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.sourcegraph.cody.vscode.Range

@JvmInline value class CompletionItemID(val value: String)

data class AutocompleteResult(
    val items: List<AutocompleteItem>,
    @Deprecated("Usage should be internal to Cody Agent")
    val completionEvent: CompletionBookkeepingEvent?,
)

data class AutocompleteItem(
    val id: CompletionItemID,
    val insertText: String,
    val range: Range,
)

val CompletionItemIDSerializer =
    JsonSerializer<CompletionItemID> { src, _, _ -> JsonPrimitive(src.value) }
