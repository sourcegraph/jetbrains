/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

data class MemoryUsage(
  val rss: Long,
  val heapTotal: Long,
  val heapUsed: Long,
  val external: Long,
  val arrayBuffers: Long,
)

