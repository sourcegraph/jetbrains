@file:Suppress("FunctionName", "ClassName")
package com.sourcegraph.cody.protocol_generated

data class ExtensionMessage(
  var type: String? = null,
  var config: ConfigParams? = null,
  var authStatus: AuthStatus? = null,
  var workspaceFolderUris: List<String>? = null,
  var localHistory: UserLocalHistory? = null,
  var messages: List<ChatMessage>? = null,
  var isMessageInProgress: Boolean? = null,
  var chatID: String? = null,
  var view: View? = null,
  var errors: String? = null,
  var notice: NoticeParams? = null,
  var isTranscriptError: Boolean? = null,
  var userContextFiles: List<ContextFile>? = null,
  var kind: ContextFileType? = null,
  var models: List<ModelProvider>? = null,
  var results: List<SearchPanelFile>? = null,
  var query: String? = null,
  var scopeDir: String? = null,
  var enhancedContextStatus: EnhancedContextContextT? = null,
  var snippet: String? = null,
  var attribution: AttributionParams? = null,
  var error: String? = null,
  var data: Boolean? = null,
  var isActive: Boolean? = null,
  var repos: List<Repo>? = null,
  var configFeatures: ConfigFeaturesParams? = null,
)

