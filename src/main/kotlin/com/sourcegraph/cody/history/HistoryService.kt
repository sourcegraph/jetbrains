package com.sourcegraph.cody.history

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.sourcegraph.cody.agent.CodyAgentClient
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ContextMessage
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.history.state.HistoryState
import com.sourcegraph.cody.history.state.MessageState
import java.time.LocalDateTime

@State(
    name = "com.sourcegraph.cody.history.HistoryService", storages = [Storage("cody_history.xml")])
@Service(Service.Level.PROJECT)
class HistoryService : SimplePersistentStateComponent<HistoryState>(HistoryState()) {

  fun addMessage(panelId: String, message: ChatMessage) {
    logger.info("add message panelId=$panelId, message=$message")
    val chat = getChatByPanelIdOrCreate(panelId)
    chat.messages +=
        MessageState().apply {
          text = message.text
          speaker = message.speaker
          setDateTime(LocalDateTime.now())
        }
  }

  fun updateMessage(panelId: String, message: ChatMessage) {
    logger.info("update message panelId=$panelId, message=$message")
    val chat = getChatByPanelIdOrCreate(panelId)
    chat.messages.lastOrNull()?.text = message.text
  }

  fun updateMessageContextFiles(panelId: String, contextMessages: List<ContextMessage>) {
    logger.info("update message context files panelId=$panelId, contextMessages=$contextMessages")
    val chat = getChatByPanelIdOrCreate(panelId)
    val lastMessage = chat.messages.last()
    lastMessage.contextFiles = contextMessages.map { it.file!!.uri.toString() }.toMutableList()
  }

  fun updateReply(panelId: String, replyChatId: String) {
    logger.info("update reply panelId=$panelId, replyChatId=$replyChatId")
    val chat = getChatByPanelIdOrCreate(panelId)
    chat.replyChatId = replyChatId
  }

  fun removeChat(panelId: String) {
    logger.info("remove chat panelId=$panelId")
    state.chats.removeIf { it.panelId == panelId }
  }

  fun getChatByPanelId(panelId: String): ChatState =
      state.chats.find { it.panelId == panelId } ?: error("Chat not found for panelId=$panelId")

  private fun getChatByPanelIdOrCreate(panelId: String): ChatState {
    // todo what if there is a lot of chats? is this `find` ok?
    val found = state.chats.find { it.panelId == panelId }
    if (found != null) return found
    println("chat created panelId=$panelId")
    val created = ChatState()
    created.panelId = panelId
    state.chats += created
    return created
  }

  companion object {

    @JvmStatic fun getInstance() = service<HistoryService>()

    private val logger = Logger.getInstance(CodyAgentClient::class.java)
  }
}
