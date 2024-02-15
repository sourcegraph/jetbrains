@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName")

package com.sourcegraph.cody.protocol_generated

data class WebviewMessage(
    val command: CommandEnum? =
        null, // Oneof: initialized, event, submit, history, restoreHistory, deleteHistory, links,
              // show-page, chatModel, get-chat-models, openFile, openLocalFileWithRange, edit,
              // context/get-remote-search-repos, context/choose-remote-search-repo,
              // context/remove-remote-search-repo, embeddings/index, symf/index, insert, newFile,
              // copy, auth, abort, reload, simplified-onboarding, getUserContext, search,
              // show-search-result, reset, attribution-search, ready
    val eventName: String? = null,
    val properties: TelemetryEventProperties? = null,
    val addEnhancedContext: Boolean? = null,
    val contextFiles: List<ContextFile>? = null,
    val text: String? = null,
    val submitType: ChatSubmitType? = null, // Oneof: user, user-newchat
    val action: ActionEnum? = null, // Oneof: clear, export
    val chatID: String? = null,
    val value: String? = null,
    val page: String? = null,
    val model: String? = null,
    val uri: Uri? = null,
    val range: ActiveTextEditorSelectionRange? = null,
    val filePath: String? = null,
    val index: Int? = null,
    val explicitRepos: List<Repo>? = null,
    val repoId: String? = null,
    val metadata: CodeBlockMeta? = null,
    val eventType: EventTypeEnum? = null, // Oneof: Button, Keydown
    val authKind: AuthKindEnum? =
        null, // Oneof: signin, signout, support, callback, simplified-onboarding,
              // simplified-onboarding-exposure
    val endpoint: String? = null,
    val authMethod: AuthMethod? = null, // Oneof: dotcom, github, gitlab, google
    val onboardingKind: OnboardingKindEnum? = null, // Oneof: web-sign-in-token
    val query: String? = null,
    val snippet: String? = null,
)

enum class CommandEnum {
  @com.google.gson.annotations.SerializedName("initialized") Initialized,
  @com.google.gson.annotations.SerializedName("event") Event,
  @com.google.gson.annotations.SerializedName("submit") Submit,
  @com.google.gson.annotations.SerializedName("history") History,
  @com.google.gson.annotations.SerializedName("restoreHistory") RestoreHistory,
  @com.google.gson.annotations.SerializedName("deleteHistory") DeleteHistory,
  @com.google.gson.annotations.SerializedName("links") Links,
  @com.google.gson.annotations.SerializedName("show-page") `show-page`,
  @com.google.gson.annotations.SerializedName("chatModel") ChatModel,
  @com.google.gson.annotations.SerializedName("get-chat-models") `get-chat-models`,
  @com.google.gson.annotations.SerializedName("openFile") OpenFile,
  @com.google.gson.annotations.SerializedName("openLocalFileWithRange") OpenLocalFileWithRange,
  @com.google.gson.annotations.SerializedName("edit") Edit,
  @com.google.gson.annotations.SerializedName("context/get-remote-search-repos")
  `context_get-remote-search-repos`,
  @com.google.gson.annotations.SerializedName("context/choose-remote-search-repo")
  `context_choose-remote-search-repo`,
  @com.google.gson.annotations.SerializedName("context/remove-remote-search-repo")
  `context_remove-remote-search-repo`,
  @com.google.gson.annotations.SerializedName("embeddings/index") Embeddings_index,
  @com.google.gson.annotations.SerializedName("symf/index") Symf_index,
  @com.google.gson.annotations.SerializedName("insert") Insert,
  @com.google.gson.annotations.SerializedName("newFile") NewFile,
  @com.google.gson.annotations.SerializedName("copy") Copy,
  @com.google.gson.annotations.SerializedName("auth") Auth,
  @com.google.gson.annotations.SerializedName("abort") Abort,
  @com.google.gson.annotations.SerializedName("reload") Reload,
  @com.google.gson.annotations.SerializedName("simplified-onboarding") `simplified-onboarding`,
  @com.google.gson.annotations.SerializedName("getUserContext") GetUserContext,
  @com.google.gson.annotations.SerializedName("search") Search,
  @com.google.gson.annotations.SerializedName("show-search-result") `show-search-result`,
  @com.google.gson.annotations.SerializedName("reset") Reset,
  @com.google.gson.annotations.SerializedName("attribution-search") `attribution-search`,
  @com.google.gson.annotations.SerializedName("ready") Ready,
}

enum class ActionEnum {
  @com.google.gson.annotations.SerializedName("clear") Clear,
  @com.google.gson.annotations.SerializedName("export") Export,
}

enum class EventTypeEnum {
  @com.google.gson.annotations.SerializedName("Button") Button,
  @com.google.gson.annotations.SerializedName("Keydown") Keydown,
}

enum class AuthKindEnum {
  @com.google.gson.annotations.SerializedName("signin") Signin,
  @com.google.gson.annotations.SerializedName("signout") Signout,
  @com.google.gson.annotations.SerializedName("support") Support,
  @com.google.gson.annotations.SerializedName("callback") Callback,
  @com.google.gson.annotations.SerializedName("simplified-onboarding") `simplified-onboarding`,
  @com.google.gson.annotations.SerializedName("simplified-onboarding-exposure")
  `simplified-onboarding-exposure`,
}

enum class OnboardingKindEnum {
  @com.google.gson.annotations.SerializedName("web-sign-in-token") `web-sign-in-token`,
}
