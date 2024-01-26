package com.sourcegraph.cody.chat

import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.ExtensionMessage
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ChatSubmitMessageParams
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ui.ChatPanel
import com.sourcegraph.cody.config.RateLimitStateManager
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.CodyBundle.fmt
import com.sourcegraph.common.UpgradeToCodyProNotification.Companion.isCodyProJetbrains
import com.sourcegraph.telemetry.GraphQlLogger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class Chat() {
  companion object {
    private val logger = LoggerFactory.getLogger(Chat::class.java)

    @Throws(ExecutionException::class, InterruptedException::class)
    fun sendMessageViaAgent(
        project: Project,
        registerNewRequest: (CompletableFuture<*>) -> Unit,
        panelId: String,
        humanMessage: ChatMessage,
        isEnhancedContextEnabled: Boolean
    ) {
      CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
        val message =
            WebviewMessage(
                command = "submit",
                text = humanMessage.actualMessage(),
                submitType = "user",
                addEnhancedContext = isEnhancedContextEnabled,
                // TODO(#242): allow to manually add files to the context via `@`
                contextFiles = listOf())

        val request = agent.server.chatSubmitMessage(ChatSubmitMessageParams(panelId, message))
        registerNewRequest(request)
      }
    }

    fun processResponse(
        project: Project,
        chatPanels: Map<String, ChatPanel>,
        panelId: String,
        extensionMessage: ExtensionMessage
    ) {
      fun getChatPanel() = chatPanels[panelId]

      try {
        GraphQlLogger.logCodyEvent(project, "chat-question", "submitted")

        val lastMessage = extensionMessage.messages?.lastOrNull()
        if (lastMessage?.error != null) {

          getChatPanel()?.getRequestToken()?.dispose()
          val rateLimitError = lastMessage.error.toRateLimitError()
          if (rateLimitError != null) {
            RateLimitStateManager.reportForChat(project, rateLimitError)
            isCodyProJetbrains(project).thenApply { isCodyPro ->
              val text =
                  when {
                    rateLimitError.upgradeIsAvailable && isCodyPro ->
                        CodyBundle.getString("chat.rate-limit-error.upgrade")
                            .fmt(rateLimitError.limit?.let { " $it" } ?: "")
                    else -> CodyBundle.getString("chat.rate-limit-error.explain")
                  }

              getChatPanel()?.addOrUpdateMessage(ChatMessage(Speaker.ASSISTANT, text))
            }
          } else {
            // Currently we ignore other kind of errors like context window limit reached
          }
        } else {
          RateLimitStateManager.invalidateForChat(project)
          GraphQlLogger.logCodyEvent(project, "chat-question", "executed")

          if (extensionMessage.isMessageInProgress == false) {
            getChatPanel()?.getRequestToken()?.dispose()
          } else {

            if (lastMessage?.text != null && extensionMessage.chatID != null) {
              // Updates of the given message will always have the same UUID
              val uuid =
                  UUID.nameUUIDFromBytes(extensionMessage.messages.count().toString().toByteArray())
              val chatMessage =
                  ChatMessage(
                      Speaker.ASSISTANT, lastMessage.text, lastMessage.displayText, id = uuid)

              getChatPanel()?.addOrUpdateMessage(chatMessage)
            }
          }
        }
      } catch (error: Exception) {
        getChatPanel()?.getRequestToken()?.dispose()
        logger.error("Error while processing the message", error)
        getChatPanel()
            ?.addOrUpdateMessage(
                ChatMessage(
                    Speaker.ASSISTANT,
                    "Cody is not able to reply at the moment. " +
                        "This is a bug, please report an issue to https://github.com/sourcegraph/jetbrains/issues/new/choose " +
                        "and include as many details as possible to help troubleshoot the problem."))
      }
    }
  }
}
