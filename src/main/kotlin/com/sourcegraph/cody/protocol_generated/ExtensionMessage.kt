@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class ExtensionMessage(
  val type: TypeEnum? = null, // Oneof: search:config, history, transcript, view, errors, notice, transcript-errors, userContextFiles, chatModels, update-search-results, index-updated, enhanced-context, attribution, setChatEnabledConfigFeature, webview-state, context/remote-repos, setConfigFeatures, config
  val config: ConfigParams? = null,
  val authStatus: AuthStatus? = null,
  val workspaceFolderUris: List<String>? = null,
  val localHistory: UserLocalHistory? = null,
  val messages: List<ChatMessage>? = null,
  val isMessageInProgress: Boolean? = null,
  val chatID: String? = null,
  val view: View? = null, // Oneof: chat, login
  val errors: String? = null,
  val notice: NoticeParams? = null,
  val isTranscriptError: Boolean? = null,
  val userContextFiles: List<ContextFile>? = null,
  val kind: ContextFileType? = null, // Oneof: file, symbol
  val models: List<ModelProvider>? = null,
  val results: List<SearchPanelFile>? = null,
  val query: String? = null,
  val scopeDir: String? = null,
  val enhancedContextStatus: EnhancedContextContextT? = null,
  val snippet: String? = null,
  val attribution: AttributionParams? = null,
  val error: String? = null,
  val data: Boolean? = null,
  val isActive: Boolean? = null,
  val repos: List<Repo>? = null,
  val configFeatures: ConfigFeaturesParams? = null,
)

enum class TypeEnum {
  @com.google.gson.annotations.SerializedName("search:config") Search_config,
  @com.google.gson.annotations.SerializedName("history") History,
  @com.google.gson.annotations.SerializedName("transcript") Transcript,
  @com.google.gson.annotations.SerializedName("view") View,
  @com.google.gson.annotations.SerializedName("errors") Errors,
  @com.google.gson.annotations.SerializedName("notice") Notice,
  @com.google.gson.annotations.SerializedName("transcript-errors") `transcript-errors`,
  @com.google.gson.annotations.SerializedName("userContextFiles") UserContextFiles,
  @com.google.gson.annotations.SerializedName("chatModels") ChatModels,
  @com.google.gson.annotations.SerializedName("update-search-results") `update-search-results`,
  @com.google.gson.annotations.SerializedName("index-updated") `index-updated`,
  @com.google.gson.annotations.SerializedName("enhanced-context") `enhanced-context`,
  @com.google.gson.annotations.SerializedName("attribution") Attribution,
  @com.google.gson.annotations.SerializedName("setChatEnabledConfigFeature") SetChatEnabledConfigFeature,
  @com.google.gson.annotations.SerializedName("webview-state") `webview-state`,
  @com.google.gson.annotations.SerializedName("context/remote-repos") `context_remote-repos`,
  @com.google.gson.annotations.SerializedName("setConfigFeatures") SetConfigFeatures,
  @com.google.gson.annotations.SerializedName("config") Config,
}

