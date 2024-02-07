package com.sourcegraph.cody.agent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.sourcegraph.cody.agent.protocol.DebugMessage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.sourcegraph.cody.agent.protocol.DisplayCodeLensParams;
import com.sourcegraph.cody.agent.protocol.EditTask;
import com.sourcegraph.cody.agent.protocol.TextDocumentEditParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Implementation of the client part of the Cody agent protocol. */
@SuppressWarnings("unused")
public class CodyAgentClient {

  private static final Logger logger = Logger.getInstance(CodyAgentClient.class);
  // Callback that is invoked when the agent sends a "chat/updateMessageInProgress" notification.
  @Nullable public Consumer<WebviewPostMessageParams> onNewMessage;

  // Callback that is invoked when the agent sends a "setConfigFeatures" message.
  @Nullable public ConfigFeaturesObserver onSetConfigFeatures;

  // Callback that is invoked on webview messages which aren't handled by onNewMessage or
  // onSetConfigFeatures
  @Nullable public Consumer<WebviewPostMessageParams> onReceivedWebviewMessage;

  @Nullable public Editor editor;

  // List of callbacks for the "editTaskState/didChange" notification.
  // This enables multiple concurrent inline editing tasks.
  private Consumer<EditTask> onEditTaskDidChange = null;

  private Consumer<TextDocumentEditParams> onTextDocumentEdit;

  public void setOnEditTaskDidChange(Consumer<EditTask> callback) {
    onEditTaskDidChange = callback;
  }

  @JsonNotification("editTaskState/didChange")
  public void editTaskStateDidChange(EditTask params) {
    onEditTaskDidChange.accept(params);
  }

  public void setOnTextDocumentEdit(Consumer<TextDocumentEditParams> callback) {
    onTextDocumentEdit = callback;
  }

  @JsonRequest("textDocument/edit")
  public CompletableFuture<Boolean> textDocumentEdit(TextDocumentEditParams params) {
    var future = new CompletableFuture<Boolean>();
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              try {
                onTextDocumentEdit.accept(params);
                future.complete(true);
              } catch (Error e) {
                future.completeExceptionally(e);
              }
            });
    return future;
  }

  @JsonNotification("codeLenses/display")
  public void codeLensesDisplay(DisplayCodeLensParams params) {
    logger.info("codeLensesDisplay");
  }

  /**
   * Helper to run client request/notification handlers on the IntelliJ event thread. Use this
   * helper for handlers that require access to the IntelliJ editor, for example to read the text
   * contents of the open editor.
   */
  private <T> @NotNull CompletableFuture<T> onEventThread(Supplier<T> handler) {
    CompletableFuture<T> result = new CompletableFuture<>();
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              try {
                result.complete(handler.get());
              } catch (Exception e) {
                result.completeExceptionally(e);
              }
            });
    return result;
  }

  // Webviews
  @JsonRequest("webview/create")
  public CompletableFuture<Void> webviewCreate(WebviewCreateParams params) {
    logger.error("webview/create This request should not happen if you are using chat/new.");
    return CompletableFuture.completedFuture(null);
  }

  // =============
  // Notifications
  // =============

  @JsonNotification("debug/message")
  public void debugMessage(@NotNull DebugMessage msg) {
    logger.warn(String.format("%s: %s", msg.getChannel(), msg.getMessage()));
  }

  @JsonNotification("webview/postMessage")
  public void webviewPostMessage(@NotNull WebviewPostMessageParams params) {
    ExtensionMessage extensionMessage = params.getMessage();

    if (onNewMessage != null
        && extensionMessage.getType().equals(ExtensionMessage.Type.TRANSCRIPT)) {
      ApplicationManager.getApplication().invokeLater(() -> onNewMessage.accept(params));
      return;
    }

    if (onSetConfigFeatures != null
        && extensionMessage.getType().equals(ExtensionMessage.Type.SET_CONFIG_FEATURES)) {
      ApplicationManager.getApplication()
          .invokeLater(() -> onSetConfigFeatures.update(extensionMessage.getConfigFeatures()));
      return;
    }

    if (onReceivedWebviewMessage != null) {
      ApplicationManager.getApplication()
          .invokeLater(() -> onReceivedWebviewMessage.accept(params));
      return;
    }

    logger.debug(String.format("webview/postMessage %s: %s", params.getId(), params.getMessage()));
  }
}
