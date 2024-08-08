@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

data class OptionsParams(
  val undoStopBefore: Boolean,
  val undoStopAfter: Boolean,
)

enableOnlyCommandUris: List<String>? = null,
  val localResourceRoots: List<String>? = null,
  val portMapping: List<PortMappingParams>,
  val enableFindWidget: Boolean,
  val retainContextWhenHidden: Boolean,
)

