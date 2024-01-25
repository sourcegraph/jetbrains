package com.sourcegraph.cody.chat

import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.ExtensionMessage
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.*
import com.sourcegraph.cody.agent.protocol.ErrorCodeUtils.toErrorCode
import com.sourcegraph.cody.agent.protocol.RateLimitError.Companion.toRateLimitError
import com.sourcegraph.cody.commands.CommandId
import com.sourcegraph.cody.config.RateLimitStateManager
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.CodyBundle.fmt
import com.sourcegraph.common.UpgradeToCodyProNotification.Companion.isCodyProJetbrains
import com.sourcegraph.telemetry.GraphQlLogger
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.function.Consumer

class Chat(private val panelID: String) {
  private val logger = LoggerFactory.getLogger(Chat::class.java)

  @Throws(ExecutionException::class, InterruptedException::class)
  fun sendMessageViaAgent(
      project: Project,
      cancellationToken: CancellationToken,
      addOrUpdateMessage: (ChatMessage) -> Unit,
      humanMessage: ChatMessage,
      commandId: CommandId?,
      isEnhancedContextEnabled: Boolean
  ) {
    CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
      agent.client.onChatUpdate = Consumer { extensionMessage ->
        if (extensionMessage.isMessageInProgress == false) {
          cancellationToken.abort()
        } else {
          val lastMsg = extensionMessage?.messages?.lastOrNull()
          if (lastMsg?.text != null && extensionMessage.chatID != null) {
            val chatMessage =
                ChatMessage(
                    Speaker.ASSISTANT,
                    lastMsg.text,
                    lastMsg.displayText,
                    id = extensionMessage.chatID)
            addOrUpdateMessage(chatMessage)
          }
        }

        // val agentChatMessageText = chatMessage?.text ?: return@Consumer
        //        val chatMessage =
        //            ChatMessage(Speaker.ASSISTANT, agentChatMessageText,
        // agentChatMessage.displayText)
        // HistoryService.getInstance().addOrUpdateMessage(chatId, lastReply.chatID!!)
        // HistoryService.getInstance().addMessage(chatId!!, message)
        // historyTree.refreshTree()
      }

      if (commandId != null) {
        // MYTODO NEW CHAT
        when (commandId) {
          CommandId.Explain -> agent.server.commandsExplain().get()
          CommandId.Smell -> agent.server.commandsSmell().get()
          CommandId.Test -> agent.server.commandsTest().get()
        }
      } else {
        processResponse(project, cancellationToken, addOrUpdateMessage) {
          agent.server.chatSubmitMessage(
              ChatSubmitMessageParams(
                  panelID,
                  WebviewMessage(
                      command = "submit",
                      text = humanMessage.actualMessage(),
                      submitType = "user",
                      addEnhancedContext = isEnhancedContextEnabled,
                      // TODO(#242): allow to manually add files to the context via `@`
                      contextFiles = listOf())))
        }
      }
    }
  }

  private fun processResponse(
      project: Project,
      cancellationToken: CancellationToken,
      addOrUpdateMessage: (ChatMessage) -> Unit,
      requestFun: () -> CompletableFuture<ExtensionMessage>
  ) {
    try {
      GraphQlLogger.logCodyEvent(project, "chat-question", "submitted")

      val request = requestFun()
      cancellationToken.onCancellationRequested { request.cancel(true) }

      request.handle { lastReply, error ->
        val chatError = lastReply.messages?.lastOrNull()?.error
        if (error != null || chatError != null) {
          cancellationToken.dispose()

          val maybeRateLimitError =
              if (error is ResponseErrorException &&
                  error.toErrorCode() == ErrorCode.RateLimitError) {
                error.toRateLimitError()
              } else chatError?.toRateLimitError()

          if (maybeRateLimitError != null) {
            reportRateLimitError(project, addOrUpdateMessage, maybeRateLimitError)
          } else {
            reportGenericError(error ?: Exception(chatError?.message), addOrUpdateMessage)
          }
        } else {
          RateLimitStateManager.invalidateForChat(project)
          GraphQlLogger.logCodyEvent(project, "chat-question", "executed")
        }
      }
    } catch (error: Exception) {
      cancellationToken.dispose()
      reportGenericError(error, addOrUpdateMessage)
    }
  }

  private fun reportGenericError(
      error: Throwable,
      addOrUpdateMessage: (ChatMessage) -> Unit,
  ) {
    logger.warn("Error while sending the message", error)
    addOrUpdateMessage(
        ChatMessage(
            Speaker.ASSISTANT,
            "Cody is not able to reply at the moment. " +
                "This is a bug, please report an issue to https://github.com/sourcegraph/jetbrains/issues/new/choose " +
                "and include as many details as possible to help troubleshoot the problem."))
  }

  private fun reportRateLimitError(
      project: Project,
      addOrUpdateMessage: (ChatMessage) -> Unit,
      rateLimitError: RateLimitError
  ) {
    RateLimitStateManager.reportForChat(project, rateLimitError)

    isCodyProJetbrains(project).thenApply { isCodyPro ->
      val text =
          when {
            rateLimitError.upgradeIsAvailable && isCodyPro ->
                CodyBundle.getString("chat.rate-limit-error.upgrade")
                    .fmt(rateLimitError.limit?.let { " $it" } ?: "")
            else -> CodyBundle.getString("chat.rate-limit-error.explain")
          }

      addOrUpdateMessage(ChatMessage(Speaker.ASSISTANT, text))
    }
  }
}
