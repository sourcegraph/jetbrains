/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

data class SerializedChatTranscript(
  val id: String,
  val chatTitle: String? = null,
  val interactions: List<SerializedChatInteraction>,
  val lastInteractionTimestamp: String,
  val enhancedContext: EnhancedContextParams? = null,
)

