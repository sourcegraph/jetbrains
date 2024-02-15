@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class CustomCommandResult(
  val type: TypeEnum? = null, // Oneof: edit, chat
  val chatResult: String? = null,
  val editResult: EditTask? = null,
)

enum class TypeEnum {
  @com.google.gson.annotations.SerializedName("edit") Edit,
  @com.google.gson.annotations.SerializedName("chat") Chat,
}

