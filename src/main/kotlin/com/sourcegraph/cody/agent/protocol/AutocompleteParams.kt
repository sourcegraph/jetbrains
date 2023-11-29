package com.sourcegraph.cody.agent.protocol

import java.net.URI

enum class AutocompleteTriggerKind(val value: String) {
  AUTOMATIC("Automatic"),
  INVOKE("Invoke"),
}

data class AutocompleteParams(
    val uri: URI,
    val position: Position,
    val triggerKind: String? = AutocompleteTriggerKind.AUTOMATIC.value,
    val selectedCompletionInfo: SelectedCompletionInfo? = null
) {
  constructor(
      filePath: String,
      position: Position,
      triggerKind: String? = AutocompleteTriggerKind.AUTOMATIC.value,
      selectedCompletionInfo: SelectedCompletionInfo? = null
  ) : this(URI("file://$filePath"), position, triggerKind, selectedCompletionInfo)
}
