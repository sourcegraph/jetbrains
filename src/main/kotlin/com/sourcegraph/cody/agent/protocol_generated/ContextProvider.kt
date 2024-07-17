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

sealed class ContextProvider {
  companion object {
    val deserializer: JsonDeserializer<ContextProvider> =
        JsonDeserializer { element: JsonElement, _: Type, context: JsonDeserializationContext ->
          when (element.getAsJsonObject().get("kind").getAsString()) {
            "embeddings" ->
                context.deserialize<LocalEmbeddingsProvider>(
                    element, LocalEmbeddingsProvider::class.java)
            "search" ->
                context.deserialize<LocalSearchProvider>(element, LocalSearchProvider::class.java)
            else -> throw Exception("Unknown discriminator ${element}")
          }
        }
  }
}

data class LocalEmbeddingsProvider(
    val kind: KindEnum, // Oneof: embeddings
    val state: StateEnum, // Oneof: indeterminate, no-match, unconsented, indexing, ready
    val errorReason: ErrorReasonEnum? = null, // Oneof: not-a-git-repo, git-repo-has-no-remote
    val embeddingsAPIProvider: EmbeddingsProvider, // Oneof: sourcegraph
) : ContextProvider() {

  enum class KindEnum {
    @SerializedName("embeddings") Embeddings,
  }

  enum class StateEnum {
    @SerializedName("indeterminate") Indeterminate,
    @SerializedName("no-match") `No-match`,
    @SerializedName("unconsented") Unconsented,
    @SerializedName("indexing") Indexing,
    @SerializedName("ready") Ready,
  }

  enum class ErrorReasonEnum {
    @SerializedName("not-a-git-repo") `Not-a-git-repo`,
    @SerializedName("git-repo-has-no-remote") `Git-repo-has-no-remote`,
  }
}

data class LocalSearchProvider(
    val kind: KindEnum, // Oneof: search
    val type: TypeEnum, // Oneof: local
    val state: StateEnum, // Oneof: unindexed, indexing, ready, failed
) : ContextProvider() {

  enum class KindEnum {
    @SerializedName("search") Search,
  }

  enum class TypeEnum {
    @SerializedName("local") Local,
  }

  enum class StateEnum {
    @SerializedName("unindexed") Unindexed,
    @SerializedName("indexing") Indexing,
    @SerializedName("ready") Ready,
    @SerializedName("failed") Failed,
  }
}

data class RemoteSearchProvider(
    val kind: KindEnum, // Oneof: search
    val type: TypeEnum, // Oneof: remote
    val state: StateEnum, // Oneof: ready, no-match
    val id: String,
    val inclusion: InclusionEnum, // Oneof: auto, manual
    val isIgnored: Boolean,
) : ContextProvider() {

  enum class KindEnum {
    @SerializedName("search") Search,
  }

  enum class TypeEnum {
    @SerializedName("remote") Remote,
  }

  enum class StateEnum {
    @SerializedName("ready") Ready,
    @SerializedName("no-match") `No-match`,
  }

  enum class InclusionEnum {
    @SerializedName("auto") Auto,
    @SerializedName("manual") Manual,
  }
}
