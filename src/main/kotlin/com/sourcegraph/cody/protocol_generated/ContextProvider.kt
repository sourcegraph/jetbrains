@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class ContextProvider(
  val kind: KindEnum? = null, // Oneof: search, search, embeddings
  val state: StateEnum? = null, // Oneof: unindexed, indexing, ready, failed, ready, no-match, indeterminate, no-match, unconsented, indexing, ready
  val errorReason: ErrorReasonEnum? = null, // Oneof: not-a-git-repo, git-repo-has-no-remote
  val type: TypeEnum? = null, // Oneof: remote, local
  val id: String? = null,
  val inclusion: InclusionEnum? = null, // Oneof: auto, manual
)

enum class KindEnum {
  @com.google.gson.annotations.SerializedName("search") Search,
  @com.google.gson.annotations.SerializedName("search") Search,
  @com.google.gson.annotations.SerializedName("embeddings") Embeddings,
}

enum class StateEnum {
  @com.google.gson.annotations.SerializedName("unindexed") Unindexed,
  @com.google.gson.annotations.SerializedName("indexing") Indexing,
  @com.google.gson.annotations.SerializedName("ready") Ready,
  @com.google.gson.annotations.SerializedName("failed") Failed,
  @com.google.gson.annotations.SerializedName("ready") Ready,
  @com.google.gson.annotations.SerializedName("no-match") `no-match`,
  @com.google.gson.annotations.SerializedName("indeterminate") Indeterminate,
  @com.google.gson.annotations.SerializedName("no-match") `no-match`,
  @com.google.gson.annotations.SerializedName("unconsented") Unconsented,
  @com.google.gson.annotations.SerializedName("indexing") Indexing,
  @com.google.gson.annotations.SerializedName("ready") Ready,
}

enum class ErrorReasonEnum {
  @com.google.gson.annotations.SerializedName("not-a-git-repo") `not-a-git-repo`,
  @com.google.gson.annotations.SerializedName("git-repo-has-no-remote") `git-repo-has-no-remote`,
}

enum class TypeEnum {
  @com.google.gson.annotations.SerializedName("remote") Remote,
  @com.google.gson.annotations.SerializedName("local") Local,
}

enum class InclusionEnum {
  @com.google.gson.annotations.SerializedName("auto") Auto,
  @com.google.gson.annotations.SerializedName("manual") Manual,
}

