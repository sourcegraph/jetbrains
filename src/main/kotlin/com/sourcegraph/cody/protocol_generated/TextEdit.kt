@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class TextEdit(
  val type: TypeEnum? = null, // Oneof: insert, delete, replace
  val range: Range? = null,
  val value: String? = null,
  val metadata: WorkspaceEditEntryMetadata? = null,
  val position: Position? = null,
)

enum class TypeEnum {
  @com.google.gson.annotations.SerializedName("insert") Insert,
  @com.google.gson.annotations.SerializedName("delete") Delete,
  @com.google.gson.annotations.SerializedName("replace") Replace,
}

