@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class PreciseContext(
  val symbol: SymbolParams? = null,
  val hoverText: List<String>? = null,
  val definitionSnippet: String? = null,
  val filePath: String? = null,
  val range: RangeParams? = null,
)

