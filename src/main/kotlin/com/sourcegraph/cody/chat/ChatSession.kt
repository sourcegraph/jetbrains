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
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.history.state.MessageState
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.CodyBundle.fmt
import com.sourcegraph.common.UpgradeToCodyProNotification.Companion.isCodyProJetbrains
import com.sourcegraph.telemetry.GraphQlLogger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import org.slf4j.LoggerFactory

typealias SessionId = String

interface ChatSession {

  fun sendMessage(text: String)

  fun receiveMessage(extensionMessage: ExtensionMessage)

  fun getCancellationToken(): CancellationToken
}

class AgentChatSession
private constructor(private val project: Project, private val internalId: String, newSessionId: CompletableFuture<SessionId>, ) :
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

  fun restoreAgentSession(agent: CodyAgent) {
    synchronized(this) {
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
  }

  override fun sendMessage(text: String) {
    synchronized(this) {
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
  }

  override fun getCancellationToken(): CancellationToken = cancellationToken.get()

  @Throws(ExecutionException::class, InterruptedException::class)
  override fun receiveMessage(extensionMessage: ExtensionMessage) {
    synchronized(this) {
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
          if (extensionMessage.messages?.isNotEmpty() == true &&
              extensionMessage.isMessageInProgress == false) {
            getCancellationToken().dispose()
          } else {
            if (lastMessage?.text != null && extensionMessage.chatID != null) {
              addAssistantResponseToChat(lastMessage.text, lastMessage.displayText)
            } else {}
          }
        }
      } catch (error: Exception) {
        getCancellationToken().dispose()
        logger.error(CodyBundle.getString("chat-session.error-title"), error)
        addAssistantResponseToChat(CodyBundle.getString("chat-session.error-title"))
      }
    }
  }

  @RequiresEdt
  private fun addMessage(message: ChatMessage) {
    synchronized(this) {
      if (messages.lastOrNull()?.id == message.id) {
        messages.removeLast()
      }
      HistoryService.getInstance().update(internalId, messages + message)
      messages.add(message)
      chatPanel.addOrUpdateMessage(message)
    }
  }

  @RequiresEdt
  private fun createCancellationToken(onCancel: () -> Unit, onFinish: () -> Unit) {
    synchronized(this) {
      val newCancellationToken = CancellationToken()
      newCancellationToken.onCancellationRequested { onCancel() }
      newCancellationToken.onFinished { onFinish() }
      cancellationToken.getAndSet(newCancellationToken).abort()
      chatPanel.registerCancellationToken(newCancellationToken)
    }
  }

  companion object {
    private val chatSessions: MutableList<AgentChatSession> = mutableListOf()

    fun getSession(sessionId: SessionId): AgentChatSession? =
        synchronized(this) { chatSessions.find { it.hasSessionId(sessionId) } }

    fun restoreAllSessions(agent: CodyAgent) {
      synchronized(this) { chatSessions.forEach { it.restoreAgentSession(agent) } }
    }

    @RequiresEdt
    fun createFromCommand(project: Project, commandId: CommandId): AgentChatSession {
      synchronized(this) {
        val sessionId =
            createNewPanel(project) { agent: CodyAgent ->
              when (commandId) {
                CommandId.Explain -> agent.server.commandsExplain()
                CommandId.Smell -> agent.server.commandsSmell()
                CommandId.Test -> agent.server.commandsTest()
              }
            }

        ApplicationManager.getApplication().executeOnPooledThread {
          GraphQlLogger.logCodyEvent(project, "command:${commandId.displayName}", "submitted")
        }

        val chatSession = getSession(sessionId.get()) ?: AgentChatSession(project, UUID.randomUUID().toString(), sessionId)

        chatSession.createCancellationToken(
            onCancel = {
              CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
                agent.server.webviewReceiveMessage(
                    WebviewReceiveMessageParams(sessionId.get(), WebviewMessage(command = "abort")))
              }
            },
            onFinish = {
              GraphQlLogger.logCodyEvent(project, "command:${commandId.displayName}", "executed")
            })

        chatSession.addMessage(ChatMessage(Speaker.HUMAN, commandId.displayName))
        chatSessions.add(chatSession)
        return chatSession
      }
    }

    @RequiresEdt
    fun createNew(project: Project): AgentChatSession {
      synchronized(AgentChatSession) {
        val sessionId = createNewPanel(project) { it.server.chatNew() }
        val chatSession = AgentChatSession(project, UUID.randomUUID().toString(), sessionId)
        chatSessions.add(chatSession)
        return chatSession
      }
    }

    fun createFromState(project: Project, state: ChatState): AgentChatSession {
      // todo do przemyslenia czy nie wrzucic w watek restore
      val sessionId = createNewPanel(project) { it.server.chatNew() }
      val chatSession = AgentChatSession(project, state.internalId!!, sessionId)
      chatSessions.add(chatSession)
      for (message in state.messages) {
        val parsed = when (val speaker = message.speaker) {
          MessageState.SpeakerState.HUMAN -> Speaker.HUMAN
          MessageState.SpeakerState.ASSISTANT -> Speaker.ASSISTANT
          else -> error("unrecognized speaker $speaker")
        }
        val chatMessage = ChatMessage(parsed, message.text, id = UUID.randomUUID())
        chatSession.messages.add(chatMessage)
        chatSession.chatPanel.addOrUpdateMessage(chatMessage)
      }
      CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
        chatSession.restoreAgentSession(agent)
      }
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
