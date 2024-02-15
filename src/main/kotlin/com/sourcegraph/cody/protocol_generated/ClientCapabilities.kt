@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class ClientCapabilities(
  val completions: CompletionsEnum? = null, // Oneof: none
  val chat: ChatEnum? = null, // Oneof: none, streaming
  val git: GitEnum? = null, // Oneof: none, disabled
  val progressBars: ProgressBarsEnum? = null, // Oneof: none, enabled
  val edit: EditEnum? = null, // Oneof: none, enabled
  val editWorkspace: EditWorkspaceEnum? = null, // Oneof: none, enabled
  val untitledDocuments: UntitledDocumentsEnum? = null, // Oneof: none, enabled
  val showDocument: ShowDocumentEnum? = null, // Oneof: none, enabled
  val codeLenses: CodeLensesEnum? = null, // Oneof: none, enabled
  val showWindowMessage: ShowWindowMessageEnum? = null, // Oneof: notification, request
)

enum class CompletionsEnum {
  @com.google.gson.annotations.SerializedName("none") None,
}

enum class ChatEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("streaming") Streaming,
}

enum class GitEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("disabled") Disabled,
}

enum class ProgressBarsEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("enabled") Enabled,
}

enum class EditEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("enabled") Enabled,
}

enum class EditWorkspaceEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("enabled") Enabled,
}

enum class UntitledDocumentsEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("enabled") Enabled,
}

enum class ShowDocumentEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("enabled") Enabled,
}

enum class CodeLensesEnum {
  @com.google.gson.annotations.SerializedName("none") None,
  @com.google.gson.annotations.SerializedName("enabled") Enabled,
}

enum class ShowWindowMessageEnum {
  @com.google.gson.annotations.SerializedName("notification") Notification,
  @com.google.gson.annotations.SerializedName("request") Request,
}

