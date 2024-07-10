@file:Suppress("FunctionName", "REDUNDANT_NULLABLE")

package com.sourcegraph.cody.agent

import com.sourcegraph.cody.agent.protocol.AttributionSearchParams
import com.sourcegraph.cody.agent.protocol.AttributionSearchResponse
import com.sourcegraph.cody.agent.protocol.AutocompleteParams
import com.sourcegraph.cody.agent.protocol.AutocompleteResult
import com.sourcegraph.cody.agent.protocol.ChatHistoryResponse
import com.sourcegraph.cody.agent.protocol.ChatModelsParams
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.agent.protocol.ChatRestoreParams
import com.sourcegraph.cody.agent.protocol.ChatSubmitMessageParams
import com.sourcegraph.cody.agent.protocol.CompletionItemParams
import com.sourcegraph.cody.agent.protocol.CurrentUserCodySubscription
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.Event
import com.sourcegraph.cody.agent.protocol.GetFeatureFlag
import com.sourcegraph.cody.agent.protocol.GetFoldingRangeParams
import com.sourcegraph.cody.agent.protocol.GetFoldingRangeResult
import com.sourcegraph.cody.agent.protocol.IgnorePolicySpec
import com.sourcegraph.cody.agent.protocol.IgnoreTestParams
import com.sourcegraph.cody.agent.protocol.IgnoreTestResponse
import com.sourcegraph.cody.agent.protocol.InlineEditParams
import com.sourcegraph.cody.agent.protocol.NetworkRequest
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import com.sourcegraph.cody.agent.protocol.RemoteRepoHasParams
import com.sourcegraph.cody.agent.protocol.RemoteRepoHasResponse
import com.sourcegraph.cody.agent.protocol.RemoteRepoListParams
import com.sourcegraph.cody.agent.protocol.RemoteRepoListResponse
import com.sourcegraph.cody.agent.protocol.TaskIdParam
import com.sourcegraph.cody.agent.protocol.TelemetryEvent
import com.sourcegraph.cody.agent.protocol_generated.ClientInfo
import com.sourcegraph.cody.agent.protocol_generated.ExtensionConfiguration
import com.sourcegraph.cody.agent.protocol_generated.Graphql_GetRepoIdsParams
import com.sourcegraph.cody.agent.protocol_generated.Graphql_GetRepoIdsResult
import com.sourcegraph.cody.agent.protocol_generated.ServerInfo
import com.sourcegraph.cody.chat.ConnectionId
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest

interface CodyAgentServer : _LegacyAgentServer, _SubsetGeneratedCodyAgentServer
// This is subset of the generated protocol bindings.
// This is only temporary until all legacy bindings are made redundant.
// Make sure to copy from the generated bindings verbatim!
interface _SubsetGeneratedCodyAgentServer {
  // ========
  // Requests
  // ========
  @JsonRequest("initialize") fun initialize(params: ClientInfo): CompletableFuture<ServerInfo>

  @JsonRequest("graphql/getRepoIds")
  fun graphql_getRepoIds(
      params: Graphql_GetRepoIdsParams
  ): CompletableFuture<Graphql_GetRepoIdsResult>
  //  @JsonRequest("graphql/currentUserId")
  //  fun graphql_currentUserId(params: Null?): CompletableFuture<String>
  //  @JsonRequest("graphql/currentUserIsPro")
  //  fun graphql_currentUserIsPro(params: Null?): CompletableFuture<Boolean>
  //  @JsonRequest("featureFlags/getFeatureFlag")
  //  fun featureFlags_getFeatureFlag(params: FeatureFlags_GetFeatureFlagParams):
  // CompletableFuture<Boolean?>
  //  @JsonRequest("graphql/getCurrentUserCodySubscription")
  //  fun graphql_getCurrentUserCodySubscription(params: Null?):
  // CompletableFuture<CurrentUserCodySubscription?>
  //  @JsonRequest("graphql/logEvent")
  //  fun graphql_logEvent(params: Event): CompletableFuture<Null?>
  //  @JsonRequest("telemetry/recordEvent")
  //  fun telemetry_recordEvent(params: TelemetryEvent): CompletableFuture<Null?>

  //  @JsonRequest("graphql/getRepoIdIfEmbeddingExists")
  //  fun graphql_getRepoIdIfEmbeddingExists(params: Graphql_GetRepoIdIfEmbeddingExistsParams):
  // CompletableFuture<String?>
  //  @JsonRequest("graphql/getRepoId")
  //  fun graphql_getRepoId(params: Graphql_GetRepoIdParams): CompletableFuture<String?>
  //  @JsonRequest("git/codebaseName")
  //  fun git_codebaseName(params: Git_CodebaseNameParams): CompletableFuture<String?>
  //  @JsonRequest("webview/didDispose")
  //  fun webview_didDispose(params: Webview_DidDisposeParams): CompletableFuture<Null?>
  //  @JsonRequest("webview/receiveMessage")
  //  fun webview_receiveMessage(params: Webview_ReceiveMessageParams): CompletableFuture<Null?>
  //  @JsonRequest("webview/receiveMessageStringEncoded")
  //  fun webview_receiveMessageStringEncoded(params: Webview_ReceiveMessageStringEncodedParams):
  // CompletableFuture<Null?>
  //  @JsonRequest("diagnostics/publish")
  //  fun diagnostics_publish(params: Diagnostics_PublishParams): CompletableFuture<Null?>
  //  @JsonRequest("testing/progress")
  //  fun testing_progress(params: Testing_ProgressParams):
  // CompletableFuture<Testing_ProgressResult>
  //  @JsonRequest("testing/networkRequests")
  //  fun testing_networkRequests(params: Null?): CompletableFuture<Testing_NetworkRequestsResult>
  //  @JsonRequest("testing/requestErrors")
  //  fun testing_requestErrors(params: Null?): CompletableFuture<Testing_RequestErrorsResult>
  //  @JsonRequest("testing/closestPostData")
  //  fun testing_closestPostData(params: Testing_ClosestPostDataParams):
  // CompletableFuture<Testing_ClosestPostDataResult>
  //  @JsonRequest("testing/memoryUsage")
  //  fun testing_memoryUsage(params: Null?): CompletableFuture<Testing_MemoryUsageResult>
  //  @JsonRequest("testing/awaitPendingPromises")
  //  fun testing_awaitPendingPromises(params: Null?): CompletableFuture<Null?>
  //  @JsonRequest("testing/workspaceDocuments")
  //  fun testing_workspaceDocuments(params: GetDocumentsParams):
  // CompletableFuture<GetDocumentsResult>
  //  @JsonRequest("testing/diagnostics")
  //  fun testing_diagnostics(params: Testing_DiagnosticsParams):
  // CompletableFuture<Testing_DiagnosticsResult>
  //  @JsonRequest("testing/progressCancelation")
  //  fun testing_progressCancelation(params: Testing_ProgressCancelationParams):
  // CompletableFuture<Testing_ProgressCancelationResult>
  //  @JsonRequest("testing/reset")
  //  fun testing_reset(params: Null?): CompletableFuture<Null?>
  //  @JsonRequest("extensionConfiguration/change")
  //  fun extensionConfiguration_change(params: ExtensionConfiguration):
  // CompletableFuture<AuthStatus?>
  //  @JsonRequest("extensionConfiguration/status")
  //  fun extensionConfiguration_status(params: Null?): CompletableFuture<AuthStatus?>
  //  @JsonRequest("textDocument/change")
  //  fun textDocument_change(params: ProtocolTextDocument):
  // CompletableFuture<TextDocument_ChangeResult>
  //  @JsonRequest("attribution/search")
  //  fun attribution_search(params: Attribution_SearchParams):
  // CompletableFuture<Attribution_SearchResult>
  //  @JsonRequest("ignore/test")
  //  fun ignore_test(params: Ignore_TestParams): CompletableFuture<Ignore_TestResult>
  //  @JsonRequest("testing/ignore/overridePolicy")
  //  fun testing_ignore_overridePolicy(params: ContextFilters?): CompletableFuture<Null?>
  //  @JsonRequest("remoteRepo/has")
  //  fun remoteRepo_has(params: RemoteRepo_HasParams): CompletableFuture<RemoteRepo_HasResult>
  //  @JsonRequest("remoteRepo/list")
  //  fun remoteRepo_list(params: RemoteRepo_ListParams): CompletableFuture<RemoteRepo_ListResult>
  //
  //  // =============
  //  // Notifications
  //  // =============
  //  @JsonNotification("initialized")
  //  fun initialized(params: Null?)
  //  @JsonNotification("exit")
  //  fun exit(params: Null?)
  @JsonNotification("extensionConfiguration/didChange")
  fun extensionConfiguration_didChange(params: ExtensionConfiguration)
  //  @JsonNotification("textDocument/didOpen")
  //  fun textDocument_didOpen(params: ProtocolTextDocument)
  //  @JsonNotification("textDocument/didChange")
  //  fun textDocument_didChange(params: ProtocolTextDocument)
  //  @JsonNotification("textDocument/didFocus")
  //  fun textDocument_didFocus(params: TextDocument_DidFocusParams)
  //  @JsonNotification("textDocument/didSave")
  //  fun textDocument_didSave(params: TextDocument_DidSaveParams)
  //  @JsonNotification("textDocument/didClose")
  //  fun textDocument_didClose(params: ProtocolTextDocument)
  //  @JsonNotification("workspace/didDeleteFiles")
  //  fun workspace_didDeleteFiles(params: DeleteFilesParams)
  //  @JsonNotification("workspace/didCreateFiles")
  //  fun workspace_didCreateFiles(params: CreateFilesParams)
  //  @JsonNotification("workspace/didRenameFiles")
  //  fun workspace_didRenameFiles(params: RenameFilesParams)
  //  @JsonNotification("$/cancelRequest")
  //  fun cancelRequest(params: CancelParams)
  //  @JsonNotification("autocomplete/clearLastCandidate")
  //  fun autocomplete_clearLastCandidate(params: Null?)
  //  @JsonNotification("autocomplete/completionSuggested")
  //  fun autocomplete_completionSuggested(params: CompletionItemParams)
  //  @JsonNotification("autocomplete/completionAccepted")
  //  fun autocomplete_completionAccepted(params: CompletionItemParams)
  //  @JsonNotification("progress/cancel")
  //  fun progress_cancel(params: Progress_CancelParams)
}

// TODO: Requests waiting to be migrated & tested for compatibility. Avoid placing new protocol
// messages here.
/**
 * Interface for the server-part of the Cody agent protocol. The implementation of this interface is
 * written in TypeScript in the file "cody/agent/src/agent.ts". The Eclipse LSP4J bindings create a
 * Java implementation of this interface by using a JVM-reflection feature called "Proxy", which
 * works similar to JavaScript Proxy.
 */
interface _LegacyAgentServer {
  @JsonRequest("shutdown") fun shutdown(): CompletableFuture<Void?>

  @JsonRequest("autocomplete/execute")
  fun autocompleteExecute(params: AutocompleteParams?): CompletableFuture<AutocompleteResult>

  @JsonRequest("telemetry/recordEvent")
  fun recordEvent(event: TelemetryEvent): CompletableFuture<Void?>

  @JsonRequest("graphql/logEvent") fun logEvent(event: Event): CompletableFuture<Void?>

  @JsonRequest("graphql/currentUserId") fun currentUserId(): CompletableFuture<String>

  // TODO(CODY-2826): Would be nice if we can generate some set of "known" feature flags from the
  // protocol
  @JsonRequest("featureFlags/getFeatureFlag")
  fun evaluateFeatureFlag(flagName: GetFeatureFlag): CompletableFuture<Boolean?>

  // TODO(CODY-2827): To avoid having to pass annoying null values we should generate a default
  // value
  @JsonRequest("graphql/getCurrentUserCodySubscription")
  fun getCurrentUserCodySubscription(): CompletableFuture<CurrentUserCodySubscription?>

  @JsonNotification("initialized") fun initialized()

  @JsonNotification("exit") fun exit()

  @JsonNotification("textDocument/didFocus")
  fun textDocumentDidFocus(document: ProtocolTextDocument)

  @JsonNotification("textDocument/didOpen") fun textDocumentDidOpen(document: ProtocolTextDocument)

  @JsonNotification("textDocument/didChange")
  fun textDocumentDidChange(document: ProtocolTextDocument)

  @JsonNotification("textDocument/didClose")
  fun textDocumentDidClose(document: ProtocolTextDocument)

  @JsonNotification("textDocument/didSave") fun textDocumentDidSave(document: ProtocolTextDocument)

  @JsonNotification("autocomplete/clearLastCandidate") fun autocompleteClearLastCandidate()

  @JsonNotification("autocomplete/completionSuggested")
  fun completionSuggested(logID: CompletionItemParams)

  @JsonNotification("autocomplete/completionAccepted")
  fun completionAccepted(logID: CompletionItemParams)

  @JsonRequest("webview/receiveMessage")
  fun webviewReceiveMessage(params: WebviewReceiveMessageParams): CompletableFuture<Any?>

  @JsonRequest("editTask/accept") fun acceptEditTask(params: TaskIdParam): CompletableFuture<Void?>

  @JsonRequest("editTask/undo") fun undoEditTask(params: TaskIdParam): CompletableFuture<Void?>

  @JsonRequest("editTask/cancel") fun cancelEditTask(params: TaskIdParam): CompletableFuture<Void?>

  @JsonRequest("editTask/getFoldingRanges")
  fun getFoldingRanges(params: GetFoldingRangeParams): CompletableFuture<GetFoldingRangeResult>

  @JsonRequest("command/execute")
  fun commandExecute(params: CommandExecuteParams): CompletableFuture<Any?>

  @JsonRequest("commands/explain") fun legacyCommandsExplain(): CompletableFuture<ConnectionId>

  @JsonRequest("commands/smell") fun legacyCommandsSmell(): CompletableFuture<ConnectionId>

  @JsonRequest("editCommands/document") fun commandsDocument(): CompletableFuture<EditTask>

  @JsonRequest("editCommands/code")
  fun commandsEdit(params: InlineEditParams): CompletableFuture<EditTask>

  @JsonRequest("editCommands/test") fun commandsTest(): CompletableFuture<EditTask>

  @JsonRequest("chat/new") fun chatNew(): CompletableFuture<String>

  @JsonRequest("chat/submitMessage")
  fun chatSubmitMessage(params: ChatSubmitMessageParams): CompletableFuture<ExtensionMessage>

  @JsonRequest("chat/models")
  fun chatModels(params: ChatModelsParams): CompletableFuture<ChatModelsResponse>

  @JsonRequest("chat/export") fun chatExport(): CompletableFuture<List<ChatHistoryResponse>>

  @JsonRequest("chat/restore")
  fun chatRestore(params: ChatRestoreParams): CompletableFuture<ConnectionId>

  @JsonRequest("attribution/search")
  fun attributionSearch(
      params: AttributionSearchParams
  ): CompletableFuture<AttributionSearchResponse>

  @JsonRequest("remoteRepo/has")
  fun remoteRepoHas(params: RemoteRepoHasParams): CompletableFuture<RemoteRepoHasResponse>

  @JsonRequest("remoteRepo/list")
  fun remoteRepoList(params: RemoteRepoListParams): CompletableFuture<RemoteRepoListResponse>

  @JsonRequest("ignore/test")
  fun ignoreTest(params: IgnoreTestParams): CompletableFuture<IgnoreTestResponse>

  @JsonRequest("testing/ignore/overridePolicy")
  fun testingIgnoreOverridePolicy(params: IgnorePolicySpec?): CompletableFuture<Unit>

  @JsonRequest("testing/requestErrors")
  fun testingRequestErrors(): CompletableFuture<List<NetworkRequest>>
}
