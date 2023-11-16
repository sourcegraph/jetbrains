package com.sourcegraph.cody.vscode

import com.sourcegraph.cody.agent.protocol.CompletionEvent

@JvmInline
value class CompletionLogID(val value: String)

data class InlineAutocompleteResult(
  val logId: CompletionLogID,
  var items: List<InlineAutocompleteItem>,
  @Deprecated("Use logId instead", ReplaceWith("logId"))
  var completionEvent: CompletionEvent? = null, 
)
