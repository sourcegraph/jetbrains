@file:Suppress("FunctionName", "ClassName")
package com.sourcegraph.cody.protocol_generated
data class ContextFile(
  var uri: Uri? = null,
  var range: ActiveTextEditorSelectionRange? = null,
  var repoName: String? = null,
  var revision: String? = null,
  var title: String? = null,
  var source: String? = null,
  var content: String? = null,
  var type: String? = null,
  var symbolName: String? = null,
  var kind: String? = null,
)

