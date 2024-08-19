/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

import com.google.gson.annotations.SerializedName;

data class CompletionItemInfo(
  val parseErrorCount: Long? = null,
  val lineTruncatedCount: Long? = null,
  val truncatedWith: TruncatedWithEnum? = null, // Oneof: tree-sitter, indentation
  val nodeTypes: NodeTypesParams? = null,
  val nodeTypesWithCompletion: NodeTypesWithCompletionParams? = null,
  val lineCount: Long,
  val charCount: Long,
  val insertText: String? = null,
  val stopReason: String? = null,
) {

  enum class TruncatedWithEnum {
    @SerializedName("tree-sitter") `Tree-sitter`,
    @SerializedName("indentation") Indentation,
  }
}

