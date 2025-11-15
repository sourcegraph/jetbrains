/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

data class TestingTelemetryEvent(
  val feature: String,
  val action: String,
  val source: SourceParams,
  val parameters: ParametersParams,
  val timestamp: String,
  val testOnlyAnonymousUserID: String? = null,
)

