package com.sourcegraph.cody.history

import com.intellij.openapi.components.*
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ContextMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.history.state.ChatId
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.history.state.MessageState
import com.sourcegraph.cody.history.state.MessageState.MessageType.CHAT_MESSAGE
import com.sourcegraph.cody.history.state.HistoryState
import org.intellij.lang.annotations.Language

@State(
    name = "com.sourcegraph.cody.history.HistoryService", storages = [Storage("cody_history.xml")])
@Service(Service.Level.PROJECT)
class HistoryService : SimplePersistentStateComponent<HistoryState>(HistoryState()) {

  private val onMessageListeners = mutableListOf<() -> Unit>()

  init {
    val noChats = state.chats.size == 0
    if (noChats) {
      startChat()
    }
  }

  fun startChat(): ChatId {
    val newChat = ChatState.newEmpty()
    newChat.messages.add(MessageState(CHAT_MESSAGE, WELCOME_TEXT, Speaker.ASSISTANT, null))
    state.chats += newChat
    state.activeChatId = newChat.id
    notifyListeners()
    return newChat.id!!
  }

  fun addMessageListener(onMessage: () -> Unit) {
    onMessageListeners += onMessage
  }

  fun addChatMessage(message: ChatMessage) {
    currentChat().messages.add(MessageState.fromChatMessage(message))
    currentChat().updateLastUpdated()
    notifyListeners()
  }

  fun addContextMessage(contextMessages: List<ContextMessage?>) {
    currentChat().messages.add(MessageState.fromContextMessages(contextMessages))
    currentChat().updateLastUpdated()
    notifyListeners()
  }

  fun updateLastMessage(message: ChatMessage) {
    currentChat().messages.last().text = message.text
    currentChat().updateLastUpdated()
  }

  fun getMessages() = currentChat().messages

  private fun currentChat(): ChatState = state.chats.find { it.id == state.activeChatId }!!

  private fun notifyListeners() {
    onMessageListeners.forEach { it() }
  }

  companion object {

    @Language("md")
    private const val WELCOME_TEXT =
        "Hello! I'm Cody. I can write code and answer questions for you. See [Cody documentation](https://docs.sourcegraph.com/cody) for help and tips."

    @JvmStatic fun getInstance() = service<HistoryService>()
  }
}
