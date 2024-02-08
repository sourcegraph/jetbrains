@file:Suppress("FunctionName", "ClassName")
package com.sourcegraph.cody.protocol_generated
data class ShowWindowMessageParams(
  var severity: String? = null,
  var message: String? = null,
  var options: MessageOptions? = null,
  var items: List<String>? = null,
)

