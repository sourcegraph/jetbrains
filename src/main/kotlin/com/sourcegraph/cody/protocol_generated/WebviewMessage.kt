@file:Suppress("FunctionName", "ClassName")
package com.sourcegraph.cody.protocol_generated

data class WebviewMessage(
  var command: String? = null, // Oneof: attribution-search, reset, show-search-result, search, getUserContext, simplified-onboarding, reload, abort, auth, copy, newFile, insert, symf/index, embeddings/index, context/remove-remote-search-repo, context/choose-remote-search-repo, context/get-remote-search-repos, edit, openLocalFileWithRange, openFile, get-chat-models, chatModel, show-page, links, deleteHistory, restoreHistory, history, submit, event, initialized, ready
  var eventName: String? = null,
  var properties: TelemetryEventProperties? = null,
  var text: String? = null,
  var submitType: ChatSubmitType? = null, // Oneof: user, user-newchat
  var addEnhancedContext: Boolean? = null,
  var contextFiles: List<ContextFile>? = null,
  var action: String? = null, // Oneof: clear, export
  var chatID: String? = null,
  var value: String? = null,
  var page: String? = null,
  var model: String? = null,
  var uri: Uri? = null,
  var range: ActiveTextEditorSelectionRange? = null,
  var filePath: String? = null,
  var index: Int? = null,
  var explicitRepos: List<Repo>? = null,
  var repoId: String? = null,
  var metadata: CodeBlockMeta? = null,
  var eventType: String? = null, // Oneof: Button, Keydown
  var authKind: String? = null, // Oneof: signin, signout, support, callback, simplified-onboarding, simplified-onboarding-exposure
  var endpoint: String? = null,
  var authMethod: AuthMethod? = null, // Oneof: dotcom, github, gitlab, google
  var onboardingKind: String? = null, // Oneof: web-sign-in-token
  var query: String? = null,
  var snippet: String? = null,
)

