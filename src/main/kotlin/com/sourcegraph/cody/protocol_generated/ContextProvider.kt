@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName")
package com.sourcegraph.cody.protocol_generated

import com.google.gson.annotations.SerializedName

data class ContextProvider(
  val kind: KindEnum? = null, // Oneof: search, search, embeddings
  val state: StateEnum? = null, // Oneof: unindexed, indexing, ready, failed, ready, no-match, indeterminate, no-match, unconsented, indexing, ready
  val errorReason: ErrorReasonEnum? = null, // Oneof: not-a-git-repo, git-repo-has-no-remote
  val type: TypeEnum? = null, // Oneof: remote, local
  val id: String? = null,
  val inclusion: InclusionEnum? = null, // Oneof: auto, manual
) {

  enum class KindEnum {
    @SerializedName("search") Search,
    @SerializedName("search") Search,
    @SerializedName("embeddings") Embeddings,
  }

  enum class StateEnum {
    @SerializedName("unindexed") Unindexed,
    @SerializedName("indexing") Indexing,
    @SerializedName("ready") Ready,
    @SerializedName("failed") Failed,
    @SerializedName("ready") Ready,
    @SerializedName("no-match") `No-match`,
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

  enum class TypeEnum {
    @SerializedName("remote") Remote,
    @SerializedName("local") Local,
  }

  enum class InclusionEnum {
    @SerializedName("auto") Auto,
    @SerializedName("manual") Manual,
  }
}

