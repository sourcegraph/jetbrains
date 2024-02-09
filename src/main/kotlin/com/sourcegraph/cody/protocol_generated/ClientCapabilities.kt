@file:Suppress("FunctionName", "ClassName")
package com.sourcegraph.cody.protocol_generated

data class ClientCapabilities(
  var completions: String? = null,
  var chat: String? = null,
  var git: String? = null,
  var progressBars: String? = null,
  var edit: String? = null,
  var editWorkspace: String? = null,
  var untitledDocuments: String? = null,
  var showDocument: String? = null,
  var codeLenses: String? = null,
  var showWindowMessage: String? = null,
)

