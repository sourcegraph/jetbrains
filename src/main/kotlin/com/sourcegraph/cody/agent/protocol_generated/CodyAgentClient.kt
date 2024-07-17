/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "REDUNDANT_NULLABLE")
package com.sourcegraph.cody.agent.protocol_generated;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import java.util.concurrent.CompletableFuture;

@Suppress("unused")
interface CodyAgentClient {
  // ========
  // Requests
  // ========
  @JsonRequest("window/showMessage")
  fun window_showMessage(params: ShowWindowMessageParams): CompletableFuture<String?>
  @JsonRequest("textDocument/edit")
  fun textDocument_edit(params: TextDocumentEditParams): CompletableFuture<Boolean>
  @JsonRequest("textDocument/openUntitledDocument")
  fun textDocument_openUntitledDocument(params: UntitledTextDocument): CompletableFuture<Boolean>
  @JsonRequest("textDocument/show")
  fun textDocument_show(params: TextDocument_ShowParams): CompletableFuture<Boolean>
  @JsonRequest("workspace/edit")
  fun workspace_edit(params: WorkspaceEditParams): CompletableFuture<Boolean>
  @JsonRequest("webview/create")
  fun webview_create(params: Webview_CreateParams): CompletableFuture<Null?>

  // =============
  // Notifications
  // =============
  @JsonNotification("debug/message")
  fun debug_message(params: DebugMessage)
  @JsonNotification("editTask/didUpdate")
  fun editTask_didUpdate(params: EditTask)
  @JsonNotification("editTask/didDelete")
  fun editTask_didDelete(params: EditTask)
  @JsonNotification("codeLenses/display")
  fun codeLenses_display(params: DisplayCodeLensParams)
  @JsonNotification("ignore/didChange")
  fun ignore_didChange(params: Null?)
  @JsonNotification("webview/postMessage")
  fun webview_postMessage(params: WebviewPostMessageParams)
  @JsonNotification("webview/postMessageStringEncoded")
  fun webview_postMessageStringEncoded(params: Webview_PostMessageStringEncodedParams)
  @JsonNotification("progress/start")
  fun progress_start(params: ProgressStartParams)
  @JsonNotification("progress/report")
  fun progress_report(params: ProgressReportParams)
  @JsonNotification("progress/end")
  fun progress_end(params: Progress_EndParams)
  @JsonNotification("remoteRepo/didChange")
  fun remoteRepo_didChange(params: Null?)
  @JsonNotification("remoteRepo/didChangeState")
  fun remoteRepo_didChangeState(params: RemoteRepoFetchState)
}
