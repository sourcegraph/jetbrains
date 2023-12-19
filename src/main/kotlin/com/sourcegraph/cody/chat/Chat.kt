package com.sourcegraph.cody.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.UpdatableChat
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.protocol.*
import com.sourcegraph.cody.agent.protocol.ErrorCodeUtils.toErrorCode
import com.sourcegraph.cody.agent.protocol.RateLimitError.Companion.toRateLimitError
import com.sourcegraph.cody.config.RateLimitStateManager
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.common.UpgradeToCodyProNotification.Companion.isCodyProJetbrains
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.stream.Collectors
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException

class Chat {
  @Throws(ExecutionException::class, InterruptedException::class)
  fun sendMessageViaAgent(
      project: Project,
      humanMessage: ChatMessage,
      recipeId: String,
      chat: UpdatableChat,
      token: CancellationToken
  ) {
    val client = CodyAgent.getClient(project)
    val codyAgentServer = CodyAgent.getInitializedServer(project)
    val isFirstMessage = AtomicBoolean(false)
    client.onFinishedProcessing = Runnable { chat.finishMessageProcessing() }
    client.onChatUpdateMessageInProgress = Consumer { agentChatMessage ->
      val agentChatMessageText = agentChatMessage.text ?: return@Consumer
      val chatMessage =
          ChatMessage(Speaker.ASSISTANT, agentChatMessageText, agentChatMessage.displayText)
      if (isFirstMessage.compareAndSet(false, true)) {
        val contextMessages =
            agentChatMessage.contextFiles
                ?.stream()
                ?.map { contextFile: ContextFile ->
                  ContextMessage(Speaker.ASSISTANT, agentChatMessageText, contextFile)
                }
                ?.collect(Collectors.toList())
                ?: emptyList()
        chat.displayUsedContext(contextMessages)
        chat.addMessageToChat(chatMessage)
      } else {
        chat.updateLastMessage(chatMessage)
      }
    }
    codyAgentServer
        .thenAcceptAsync(
            { server ->
              try {
                val recipesExecuteFuture =
                    server.recipesExecute(
                        ExecuteRecipeParams(recipeId, humanMessage.actualMessage()))
                token.onCancellationRequested { recipesExecuteFuture.cancel(true) }
                recipesExecuteFuture.handle { _, error ->
                  if (error != null) {
                    handleError(project, error, chat)
                    null
                  } else {
                    RateLimitStateManager.invalidateForChat(project)
                  }
                }
              } catch (ignored: Exception) {
                // Ignore bugs in the agent when executing recipes
              }
            },
            CodyAgent.executorService)
        .get()
  }

  private fun handleError(project: Project, throwable: Throwable, chat: UpdatableChat) {
    if (throwable is ResponseErrorException) {
      val errorCode = throwable.toErrorCode()
      if (errorCode == ErrorCode.RateLimitError) {
        val rateLimitError = throwable.toRateLimitError()
        RateLimitStateManager.reportForChat(project, rateLimitError)

        ApplicationManager.getApplication().executeOnPooledThread {
          val codyProJetbrains = isCodyProJetbrains(project)
          val text =
              when {
                rateLimitError.upgradeIsAvailable && codyProJetbrains -> {
                  "<b>You've used up your chat and commands for the month:</b> " +
                      "You've used all${rateLimitError.limit?.let { " $it" }} chat messages and commands for the month. " +
                      "Upgrade to Cody Pro for unlimited autocompletes, chats, and commands. " +
                      "<a href=\"https://sourcegraph.com/cody/subscription\">Upgrade</a> " +
                      "or <a href=\"https://sourcegraph.com/cody/subscription\">learn more</a>.<br><br>" +
                      "(Already upgraded to Pro? Restart your IDE for changes to take effect)"
                }
                else -> {
                  "<b>Thank you for using Cody so heavily today!</b>" +
                      " To ensure that Cody can stay operational for all Cody users, please come back tomorrow for more chats, commands, and autocompletes." +
                      " <a href=\"https://sourcegraph.com/docs/cody/core-concepts/cody-gateway#rate-limits-and-quotas\">Learn more.</a>"
                }
              }

          val chatMessage = ChatMessage(Speaker.ASSISTANT, text, null)
          chat.addMessageToChat(chatMessage)
          chat.finishMessageProcessing()
        }
        return
      }
    }
    RateLimitStateManager.invalidateForChat(project)

    // todo: error handling for other error codes and throwables
    chat.finishMessageProcessing()
  }
}
