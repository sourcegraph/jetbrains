/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")

package com.sourcegraph.cody.agent.protocol_generated

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

sealed class ContextItem {
  companion object {
    val deserializer: JsonDeserializer<ContextItem> =
        JsonDeserializer { element: JsonElement, _: Type, context: JsonDeserializationContext ->
          when (element.getAsJsonObject().get("type").getAsString()) {
            "file" -> context.deserialize<ContextItemFile>(element, ContextItemFile::class.java)
            "repository" ->
                context.deserialize<ContextItemRepository>(
                    element, ContextItemRepository::class.java)
            "tree" -> context.deserialize<ContextItemTree>(element, ContextItemTree::class.java)
            "symbol" ->
                context.deserialize<ContextItemSymbol>(element, ContextItemSymbol::class.java)
            "openctx" ->
                context.deserialize<ContextItemOpenCtx>(element, ContextItemOpenCtx::class.java)
            else -> throw Exception("Unknown discriminator ${element}")
          }
        }
  }
}

data class ContextItemFile(
    val uri: String,
    val range: RangeData? = null,
    val content: String? = null,
    val repoName: String? = null,
    val revision: String? = null,
    val title: String? = null,
    val description: String? = null,
    val source: ContextItemSource? =
        null, // Oneof: embeddings, user, editor, search, initial, unified, selection, terminal,
    // uri, history
    val size: Int? = null,
    val isIgnored: Boolean? = null,
    val isTooLarge: Boolean? = null,
    val provider: String? = null,
    val icon: String? = null,
    val type: TypeEnum, // Oneof: file
    val remoteRepositoryName: String? = null,
) : ContextItem() {

  enum class TypeEnum {
    @SerializedName("file") File,
  }
}

data class ContextItemRepository(
    val uri: String,
    val range: RangeData? = null,
    val content: String? = null,
    val repoName: String? = null,
    val revision: String? = null,
    val title: String? = null,
    val description: String? = null,
    val source: ContextItemSource? =
        null, // Oneof: embeddings, user, editor, search, initial, unified, selection, terminal,
    // uri, history
    val size: Int? = null,
    val isIgnored: Boolean? = null,
    val isTooLarge: Boolean? = null,
    val provider: String? = null,
    val icon: String? = null,
    val type: TypeEnum, // Oneof: repository
    val repoID: String,
) : ContextItem() {

  enum class TypeEnum {
    @SerializedName("repository") Repository,
  }
}

data class ContextItemTree(
    val uri: String,
    val range: RangeData? = null,
    val content: String? = null,
    val repoName: String? = null,
    val revision: String? = null,
    val title: String? = null,
    val description: String? = null,
    val source: ContextItemSource? =
        null, // Oneof: embeddings, user, editor, search, initial, unified, selection, terminal,
    // uri, history
    val size: Int? = null,
    val isIgnored: Boolean? = null,
    val isTooLarge: Boolean? = null,
    val provider: String? = null,
    val icon: String? = null,
    val type: TypeEnum, // Oneof: tree
    val isWorkspaceRoot: Boolean,
    val name: String,
) : ContextItem() {

  enum class TypeEnum {
    @SerializedName("tree") Tree,
  }
}

data class ContextItemSymbol(
    val uri: String,
    val range: RangeData? = null,
    val content: String? = null,
    val repoName: String? = null,
    val revision: String? = null,
    val title: String? = null,
    val description: String? = null,
    val source: ContextItemSource? =
        null, // Oneof: embeddings, user, editor, search, initial, unified, selection, terminal,
    // uri, history
    val size: Int? = null,
    val isIgnored: Boolean? = null,
    val isTooLarge: Boolean? = null,
    val provider: String? = null,
    val icon: String? = null,
    val type: TypeEnum, // Oneof: symbol
    val symbolName: String,
    val kind: SymbolKind, // Oneof: class, function, method
    val remoteRepositoryName: String? = null,
) : ContextItem() {

  enum class TypeEnum {
    @SerializedName("symbol") Symbol,
  }
}

data class ContextItemOpenCtx(
    val uri: String,
    val range: RangeData? = null,
    val content: String? = null,
    val repoName: String? = null,
    val revision: String? = null,
    val title: String? = null,
    val description: String? = null,
    val source: ContextItemSource? =
        null, // Oneof: embeddings, user, editor, search, initial, unified, selection, terminal,
    // uri, history
    val size: Int? = null,
    val isIgnored: Boolean? = null,
    val isTooLarge: Boolean? = null,
    val provider: String? = null,
    val icon: String? = null,
    val type: TypeEnum, // Oneof: openctx
    val providerUri: String,
    val mention: MentionParams? = null,
) : ContextItem() {

  enum class TypeEnum {
    @SerializedName("openctx") Openctx,
  }
}
