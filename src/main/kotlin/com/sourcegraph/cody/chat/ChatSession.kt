package com.sourcegraph.cody.chat

import CodyAgent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xml.util.XmlStringUtil
import com.jetbrains.rd.util.AtomicReference
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.ExtensionMessage
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.WebviewReceiveMessageParams
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

class AgentChatSession
private constructor(private val project: Project, newSessionId: CompletableFuture<SessionId>) :
    ChatSession {

  /**
   * There are situations (like startup of tne chat) when we want to show UI immediately, but we
   * have not established connection with the agent yet. This is why we use CompletableFuture to
   * store the sessionId.
   */
  private val sessionId: AtomicReference<CompletableFuture<SessionId>> =
      AtomicReference(newSessionId)

  private val chatPanel: ChatPanel = ChatPanel(project, this)

  private val cancellationToken = AtomicReference(CancellationToken())

  private val messages = mutableListOf<ChatMessage>()

  private val logger = LoggerFactory.getLogger(ChatSession::class.java)

  init {
    cancellationToken.get().dispose()
  }

  fun getPanel(): ChatPanel = chatPanel

  fun hasSessionId(thatSessionId: SessionId): Boolean =
      sessionId.get().getNow(null) == thatSessionId

  @GuardedBy("this")
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

      GraphQlLogger.logCodyEvent(project, "chat-question", "submitted")

      ApplicationManager.getApplication().invokeLater {
        createCancellationToken(
            onCancel = { request.cancel(true) },
            onFinish = { GraphQlLogger.logCodyEvent(project, "chat-question", "executed") })
        addMessage(humanMessage)
      }
    }
  }

  override fun getCancellationToken(): CancellationToken = cancellationToken.get()

  @GuardedBy("this")
  @Throws(ExecutionException::class, InterruptedException::class)
  override fun receiveMessage(extensionMessage: ExtensionMessage) {

    fun addAssistantResponseToChat(text: String, displayText: String? = null) {
      // Updates of the given message will always have the same UUID
      val messageId =
          UUID.nameUUIDFromBytes(extensionMessage.messages?.count().toString().toByteArray())
      ApplicationManager.getApplication().invokeLater {
        addMessage(ChatMessage(Speaker.ASSISTANT, text, displayText, id = messageId))
      }
    }

    try {
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
      logger.error(CodyBundle.getString("chat-session.error-title"), error)
      addAssistantResponseToChat(CodyBundle.getString("chat-session.error-title"))
    }
  }

  @RequiresEdt
  @GuardedBy("this")
  private fun addMessage(message: ChatMessage) {
    if (messages.lastOrNull()?.id == message.id) messages.removeLast()
    messages.add(message)
    chatPanel.addOrUpdateMessage(message)
  }

  private fun createCancellationToken(onCancel: () -> Unit, onFinish: () -> Unit) {
    val newCancellationToken = CancellationToken()
    newCancellationToken.onCancellationRequested { onCancel() }
    newCancellationToken.onFinished { onFinish() }
    cancellationToken.getAndSet(newCancellationToken).abort()
    chatPanel.registerCancellationToken(newCancellationToken)
  }

  companion object {
    private val chatSessions: MutableList<AgentChatSession> = mutableListOf()

    @GuardedBy("this")
    fun getSession(sessionId: SessionId) = chatSessions.find { it.hasSessionId(sessionId) }

    @GuardedBy("this")
    fun restoreAllSessions(agent: CodyAgent) {
      chatSessions.forEach { it.restoreAgentSession(agent) }
    }

    @GuardedBy("this")
    fun createFromCommand(project: Project, commandId: CommandId): AgentChatSession {
      val sessionId =
          createNewPanel(project) { agent: CodyAgent ->
            when (commandId) {
              CommandId.Explain -> agent.server.commandsExplain()
              CommandId.Smell -> agent.server.commandsSmell()
              CommandId.Test -> agent.server.commandsTest()
            }
          }

      val chatSession = AgentChatSession(project, sessionId)
      val humanMessage = ChatMessage(Speaker.HUMAN, commandId.displayName)

      val component = "command:${commandId.displayName}"
      GraphQlLogger.logCodyEvent(project, component, "submitted")

      val waitForConsistentUIState = CompletableFuture<Unit>()
      ApplicationManager.getApplication().invokeLater {
        chatSession.createCancellationToken(
            onCancel = {
              CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
                agent.server.webviewReceiveMessage(
                    WebviewReceiveMessageParams(sessionId.get(), WebviewMessage(command = "abort")))
              }
            },
            onFinish = { GraphQlLogger.logCodyEvent(project, component, "executed") })
        chatSession.addMessage(humanMessage)
        waitForConsistentUIState.complete(Unit)
      }
      waitForConsistentUIState.get()

      chatSessions.add(chatSession)
      return chatSession
    }

    @GuardedBy("this")
    fun createNew(project: Project): AgentChatSession {
      val sessionId = createNewPanel(project) { it.server.chatNew() }
      val chatSession = AgentChatSession(project, sessionId)
      chatSessions.add(chatSession)
      return chatSession
    }

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
