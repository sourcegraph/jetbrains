/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

import com.google.gson.annotations.SerializedName;
import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import java.lang.reflect.Type;

sealed class TextEdit {
  companion object {
    val deserializer: JsonDeserializer<TextEdit> =
      JsonDeserializer { element: JsonElement, _: Type, context: JsonDeserializationContext ->
        when (element.getAsJsonObject().get("type").getAsString()) {
          "replace" -> context.deserialize<ReplaceTextEdit>(element, ReplaceTextEdit::class.java)
          "insert" -> context.deserialize<InsertTextEdit>(element, InsertTextEdit::class.java)
          "delete" -> context.deserialize<DeleteTextEdit>(element, DeleteTextEdit::class.java)
          else -> throw Exception("Unknown discriminator ${element}")
        }
      }
  }
}

data class ReplaceTextEdit(
  val type: TypeEnum, // Oneof: replace
  val range: Range,
  val value: String,
  val metadata: WorkspaceEditEntryMetadata? = null,
) : TextEdit() {

  enum class TypeEnum {
    @SerializedName("replace") Replace,
  }
}

data class InsertTextEdit(
  val type: TypeEnum, // Oneof: insert
  val position: Position,
  val value: String,
  val metadata: WorkspaceEditEntryMetadata? = null,
) : TextEdit() {

  enum class TypeEnum {
    @SerializedName("insert") Insert,
  }
}

data class DeleteTextEdit(
  val type: TypeEnum, // Oneof: delete
  val range: Range,
  val metadata: WorkspaceEditEntryMetadata? = null,
) : TextEdit() {

  enum class TypeEnum {
    @SerializedName("delete") Delete,
  }
}

