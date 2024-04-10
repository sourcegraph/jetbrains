package com.sourcegraph.cody.history

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.history.state.AccountHistoryState
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.history.state.EnhancedContextState
import com.sourcegraph.cody.history.state.HistoryState
import com.sourcegraph.cody.history.state.LLMState
import com.sourcegraph.cody.history.state.MessageState
import java.time.LocalDateTime

@State(name = "ChatHistory", storages = [Storage("cody_history.xml")])
@Service(Service.Level.PROJECT)
class HistoryService(private val project: Project) :
    SimplePersistentStateComponent<HistoryState>(HistoryState()) {

  private val listeners = mutableListOf<(ChatState) -> Unit>()

  fun listenOnUpdate(listener: (ChatState) -> Unit) {
    synchronized(listeners) { listeners += listener }
  }

  @Synchronized
  fun updateChatLlmProvider(internalId: String, llmState: LLMState) {
    getOrCreateChat(internalId).llm = llmState
  }

  @Synchronized
  fun updateChatMessages(internalId: String, chatMessages: List<ChatMessage>) {
    val found = getOrCreateChat(internalId)
    if (found.messages.size < chatMessages.size) {
      found.setUpdatedTimeAt(LocalDateTime.now())
    }

    chatMessages.map(::convertToMessageState).forEachIndexed { index, messageState ->
      val messageToUpdate = found.messages.getOrNull(index)
      if (messageToUpdate != null) {
        found.messages[index] = messageState
      } else {
        found.messages.add(messageState)
      }
    }
    synchronized(listeners) { listeners.forEach { it(found) } }
  }

  @Synchronized
  fun updateContextState(internalId: String, contextState: EnhancedContextState?) {
    if (contextState != null) {
      val found = getOrCreateChat(internalId)
      found.enhancedContext = EnhancedContextState()
      found.enhancedContext?.copyFrom(contextState)
    }
  }

  @Synchronized
  fun updateDefaultContextState(contextState: EnhancedContextState) {
    getOrCreateActiveAccountHistory().defaultEnhancedContext = EnhancedContextState()
    getOrCreateActiveAccountHistory().defaultEnhancedContext?.copyFrom(contextState)
  }

  @Synchronized
  fun getContextReadOnly(internalId: String): EnhancedContextState? {
    return copyEnhancedContextState(
        getOrCreateActiveAccountHistory()
            .chats
            .find { it.internalId == internalId }
            ?.enhancedContext)
  }

  @Synchronized
  fun getDefaultContextReadOnly(): EnhancedContextState? {
    return copyEnhancedContextState(getOrCreateActiveAccountHistory().defaultEnhancedContext)
  }

  @Synchronized
  fun getDefaultLlm(): LLMState? {
    val account = CodyAuthenticationManager.getInstance(project).getActiveAccount()
    val llm = account?.let { findHistory(it.id) }?.defaultLlm
    if (llm == null) return null
    return LLMState().also { it.copyFrom(llm) }
  }

  @Synchronized
  fun setDefaultLlm(defaultLlm: LLMState) {
    val newDefaultLlm = LLMState()
    newDefaultLlm.copyFrom(defaultLlm)
    getOrCreateActiveAccountHistory().defaultLlm = newDefaultLlm
  }

  @Synchronized
  fun remove(internalId: String?) {
    getOrCreateActiveAccountHistory().chats.removeIf { it.internalId == internalId }
  }

  @Synchronized
  fun removeAll() {
    getOrCreateActiveAccountHistory().chats = mutableListOf()
  }

  @Synchronized
  fun findActiveAccountChat(internalId: String): ChatState? =
      getActiveAccountHistory()?.chats?.find { it.internalId == internalId }

  private fun copyEnhancedContextState(context: EnhancedContextState?): EnhancedContextState? {
    if (context == null) return null

    val copy = EnhancedContextState()
    copy.copyFrom(context)
    return copy
  }

  private fun convertToMessageState(chatMessage: ChatMessage): MessageState {
    val message =
        MessageState().also {
          it.text = chatMessage.text
          it.speaker =
              when (chatMessage.speaker) {
                Speaker.HUMAN -> MessageState.SpeakerState.HUMAN
                Speaker.ASSISTANT -> MessageState.SpeakerState.ASSISTANT
              }
        }
    return message
  }

  private fun getOrCreateChat(internalId: String): ChatState {
    val accountHistory = getOrCreateActiveAccountHistory()
    val found = accountHistory.chats.find { it.internalId == internalId }
    return found ?: ChatState.create(internalId).also { accountHistory.chats += it }
  }

  private fun findHistory(accountId: String): AccountHistoryState? =
      state.accountHistories.find { it.accountId == accountId }

  @Synchronized
  fun getActiveAccountHistory(): AccountHistoryState? =
      CodyAuthenticationManager.getInstance(project).getActiveAccount()?.let { findHistory(it.id) }

  private fun getOrCreateActiveAccountHistory(): AccountHistoryState {
    val activeAccount =
        CodyAuthenticationManager.getInstance(project).getActiveAccount()
            ?: throw IllegalStateException("No active account")

    val existingHistory = findHistory(activeAccount.id)
    return existingHistory
        ?: AccountHistoryState.create(activeAccount.id).also { state.accountHistories += it }
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): HistoryService = project.service<HistoryService>()
  }
}
