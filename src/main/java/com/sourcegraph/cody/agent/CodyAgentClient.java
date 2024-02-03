package com.sourcegraph.cody.agent;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.sourcegraph.cody.agent.protocol.DebugMessage;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.jetbrains.annotations.Nullable;

/** Implementation of the client part of the Cody agent protocol. */
@SuppressWarnings("unused")
public class CodyAgentClient {

  private static final Logger logger = Logger.getInstance(CodyAgentClient.class);
  // Callback that is invoked when the agent sends a "chat/updateMessageInProgress" notification.
  @Nullable public Consumer<WebviewPostMessageParams> onNewMessage;
  // Callback that is invoked when the agent sends a "setConfigFeatures" message.
  @Nullable public ConfigFeaturesObserver onSetConfigFeatures;
  @Nullable public Editor editor;

  /**
   * Helper to run client request/notification handlers on the IntelliJ event thread. Use this
   * helper for handlers that require access to the IntelliJ editor, for example to read the text
   * contents of the open editor.
   */
  private <T> CompletableFuture<T> onEventThread(Supplier<T> handler) {
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

  // =============
  // Notifications
  // =============

  @JsonNotification("debug/message")
  public void debugMessage(DebugMessage msg) {
    logger.warn(String.format("%s: %s", msg.getChannel(), msg.getMessage()));
  }

  // Webviews
  @JsonRequest("webview/create")
  public CompletableFuture<Void> webviewCreate(WebviewCreateParams params) {
    logger.error("webview/create This request should not happen if you are using chat/new.");
    return CompletableFuture.completedFuture(null);
  }

  @JsonNotification("webview/postMessage")
  public void webviewPostMessage(WebviewPostMessageParams params) {
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
    }

    var listener = this.webviewMessageListeners.get(params.getId());
    if (listener == null) {
      logger.debug(
          String.format("webview/postMessage %s: %s", params.getId(), params.getMessage()));
    } else {
      listener.accept(params.getMessage());
    }
  }

  private final ConcurrentHashMap<String, Consumer<ExtensionMessage>> webviewMessageListeners =
      new ConcurrentHashMap<>();

  /**
   * Register a callback that will be invoked when the webview with the given panelID sends a
   * message. If an existing callback has already been registered for the given panelID, it will be
   * replaced. Returns a weak-referencing disposable that unregisters the callback when disposed.
   */
  public Disposable onWebviewMessage(String panelID, Consumer<ExtensionMessage> callback) {
    webviewMessageListeners.put(panelID, callback);
    var webviewMessageListenersRef = new WeakReference<>(webviewMessageListeners);
    return () -> {
      // Remove the callback from the map if the map is still alive.
      var webviewMessageListeners = webviewMessageListenersRef.get();
      if (webviewMessageListeners == null) {
        return;
      }
      webviewMessageListeners
          .entrySet()
          .removeIf(entry -> entry.getKey().equals(panelID) && entry.getValue() == callback);
    };
  }
}
