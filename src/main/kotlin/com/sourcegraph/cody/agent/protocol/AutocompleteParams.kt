package com.sourcegraph.cody.agent.protocol

import com.google.gson.annotations.SerializedName
import com.sourcegraph.cody.vscode.Range

enum class AutocompleteTriggerKind {
  @SerializedName("Automatic") AUTOMATIC,
  @SerializedName("Invoke") INVOKE,
}

data class AutocompleteParams(
    val filePath: String,
    val position: Position,
    val triggerKind: AutocompleteTriggerKind? = AutocompleteTriggerKind.AUTOMATIC,
    val selectedCompletionInfo: SelectedCompletionInfo? = null
)

data class SelectedCompletionInfo(val text: String, val range: Range)
