package com.sourcegraph.cody.chat

import CodyAgent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.xml.util.XmlStringUtil
import com.jetbrains.rd.util.AtomicReference
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.ExtensionMessage
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ChatRestoreParams
import com.sourcegraph.cody.agent.protocol.ChatSubmitMessageParams
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ui.ChatPanel
import com.sourcegraph.cody.commands.CommandId
import com.sourcegraph.cody.config.RateLimitStateManager
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.CodyBundle.fmt
import com.sourcegraph.common.UpgradeToCodyProNotification.Companion.isCodyProJetbrains
import com.sourcegraph.telemetry.GraphQlLogger
import io.ktor.util.collections.*
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.annotation.concurrent.GuardedBy

typealias SessionId = String

interface ChatSession {

  fun sendMessage(text: String)

  fun receiveMessage(extensionMessage: ExtensionMessage)

  fun getCancellationToken(): CancellationToken
}

class AgentChatSession : ChatSession {
  private val project: Project

  /**
   * There are situations (like startup of tne chat) when we want to show UI immediately, but we
   * have not established connection with the agent yet. This is why we use CompletableFuture to
   * store the sessionId.
   */
  private val sessionId: AtomicReference<CompletableFuture<SessionId>>

  private val chatPanel: ChatPanel

  private val cancellationToken = AtomicReference(CancellationToken())

  private val messages = ConcurrentList<ChatMessage>()

  private val logger = LoggerFactory.getLogger(ChatSession::class.java)

  private constructor(project: Project, newSessionId: CompletableFuture<SessionId>) {
    this.project = project
    this.sessionId = AtomicReference(newSessionId)
    this.chatPanel = ChatPanel(project, this)
    cancellationToken.get().dispose()
  }

  constructor(project: Project) : this(project, createNewPanel(project) { it.server.chatNew() })

  constructor(
      project: Project,
      commandId: CommandId
  ) : this(
      project,
      createNewPanel(project) { agent: CodyAgent ->
        when (commandId) {
          CommandId.Explain -> agent.server.commandsExplain()
          CommandId.Smell -> agent.server.commandsSmell()
          CommandId.Test -> agent.server.commandsTest()
        }
      })

  fun getPanel(): ChatPanel = chatPanel

  fun hasSessionId(thatSessionId: SessionId): Boolean =
      sessionId.get().getNow(null) == thatSessionId

  fun restoreAgentSession(agent: CodyAgent) {
    /**
     * TODO: We should get and save that information after panel is created or model is changed by
     *   user. Also, `chatId` parameter doesn't really matter as long as it's unique, we need to
     *   refactor Cody to not require it at all
     */
    val model = "openai/gpt-3.5-turbo"
    val restoreParams = ChatRestoreParams(model, messages.toList(), UUID.randomUUID().toString())
    val newSessionId = agent.server.chatRestore(restoreParams)
    sessionId.getAndSet(newSessionId)
  }

  @GuardedBy("this")
  override fun sendMessage(text: String) {
    val displayText = XmlStringUtil.escapeString(text)
    val humanMessage = ChatMessage(Speaker.HUMAN, text, displayText)

    CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
      val message =
          WebviewMessage(
              command = "submit",
              text = humanMessage.actualMessage(),
              submitType = "user",
              addEnhancedContext = chatPanel.isEnhancedContextEnabled(),
              // TODO(#242): allow to manually add files to the context via `@`
              contextFiles = listOf())

      val request =
          agent.server.chatSubmitMessage(ChatSubmitMessageParams(sessionId.get().get(), message))

      val newCancellationToken = CancellationToken()
      newCancellationToken.onCancellationRequested { request.cancel(true) }
      cancellationToken.getAndSet(newCancellationToken).abort()

      ApplicationManager.getApplication().invokeLater {
        chatPanel.registerCancellationToken(newCancellationToken)
      }

      addMessage(humanMessage)
    }
  }

  override fun getCancellationToken(): CancellationToken = cancellationToken.get()

  @Throws(ExecutionException::class, InterruptedException::class)
  override fun receiveMessage(extensionMessage: ExtensionMessage) {

    fun addAssistantResponseToChat(text: String, displayText: String? = null) {
      // Updates of the given message will always have the same UUID
      val messageId =
          UUID.nameUUIDFromBytes(extensionMessage.messages?.count().toString().toByteArray())
      addMessage(ChatMessage(Speaker.ASSISTANT, text, displayText, id = messageId))
    }

    try {
      GraphQlLogger.logCodyEvent(project, "chat-question", "submitted")

      val lastMessage = extensionMessage.messages?.lastOrNull()

      if (lastMessage?.error != null && extensionMessage.isMessageInProgress == false) {

        getCancellationToken().dispose()
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

            addAssistantResponseToChat(text)
          }
        } else {
          // Currently we ignore other kind of errors like context window limit reached
        }
      } else {
        RateLimitStateManager.invalidateForChat(project)
        GraphQlLogger.logCodyEvent(project, "chat-question", "executed")

        if (extensionMessage.isMessageInProgress == false) {
          getCancellationToken().dispose()
        } else {

          if (lastMessage?.text != null && extensionMessage.chatID != null) {
            addAssistantResponseToChat(lastMessage.text, lastMessage.displayText)
          }
        }
      }
    } catch (error: Exception) {
      getCancellationToken().dispose()
      logger.error("Error while processing the message", error)
      addAssistantResponseToChat(
          "Cody is not able to reply at the moment. " +
              "This is a bug, please report an issue to https://github.com/sourcegraph/jetbrains/issues/new/choose " +
              "and include as many details as possible to help troubleshoot the problem.")
    }
  }

  private fun addMessage(message: ChatMessage) {
    if (messages.lastOrNull()?.id == message.id) messages.removeLast()
    messages.add(message)
    ApplicationManager.getApplication().invokeLater { chatPanel.addOrUpdateMessage(message) }
  }

  companion object {
    private fun createNewPanel(
        project: Project,
        newPanelAction: (CodyAgent) -> CompletableFuture<String>
    ): CompletableFuture<SessionId> {
      val sessionId = CompletableFuture<SessionId>()
      CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
        try {
          sessionId.complete(newPanelAction(agent).get())
        } catch (e: ExecutionException) {
          // Agent cannot gracefully recover when connection is lost, we need to restart it
          // TODO https://github.com/sourcegraph/jetbrains/issues/306
          CodyToolWindowContent.logger.warn("Failed to load new chat, restarting agent", e)
          CodyAgentService.getInstance(project).restartAgent(project)
          Thread.sleep(5000)
          createNewPanel(project, newPanelAction)
        }
      }
      return sessionId
    }
  }
}
