@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class WorkspaceEditOperation(
  val type: TypeEnum? = null, // Oneof: rename-file, delete-file, edit-file, create-file
  val uri: String? = null,
  val options: WriteFileOptions? = null,
  val textContents: String? = null,
  val metadata: WorkspaceEditEntryMetadata? = null,
  val oldUri: String? = null,
  val newUri: String? = null,
  val deleteOptions: DeleteOptionsParams? = null,
  val edits: List<TextEdit>? = null,
)

enum class TypeEnum {
  @com.google.gson.annotations.SerializedName("rename-file") `rename-file`,
  @com.google.gson.annotations.SerializedName("delete-file") `delete-file`,
  @com.google.gson.annotations.SerializedName("edit-file") `edit-file`,
  @com.google.gson.annotations.SerializedName("create-file") `create-file`,
}

