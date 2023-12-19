package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ContextFile
import com.sourcegraph.cody.agent.protocol.ContextMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.history.state.MessageState.MessageType.CHAT_MESSAGE
import com.sourcegraph.cody.history.state.MessageState.MessageType.CONTEXT_MESSAGE

class MessageState(
    type: MessageType?,
    text: String?,
    speaker: Speaker?,
    contextFiles: List<String>?
) : BaseState() {

  var type by enum<MessageType>()
  var text by string()
  var speaker by enum<Speaker>()
  var contextFiles by list<String>()

  constructor() :
      this(
          null,
          null,
          null,
          null) // todo can we remove that? (empty constructor is required for deserialization)

  init {
    this.type = type
    this.text = text
    this.speaker = speaker
    this.contextFiles = contextFiles?.toMutableList() ?: mutableListOf() // todo simpler?
  }

  fun asChatMessage() = ChatMessage(speaker!!, text!!)

  fun asListOfContextMessages() =
      contextFiles.map {
        ContextMessage(Speaker.ASSISTANT, text ?: "", ContextFile(it, null, null))
      }

  companion object {

    fun fromChatMessage(msg: ChatMessage) =
        MessageState(
            type = CHAT_MESSAGE, text = msg.text!!, speaker = msg.speaker, contextFiles = null)

    fun fromContextMessages(contextMessages: List<ContextMessage?>) =
        MessageState(
            type = CONTEXT_MESSAGE,
            text = null,
            speaker = null,
            contextFiles = contextMessages.map { it!!.file!!.fileName })
  }

  enum class MessageType {

    CHAT_MESSAGE,
    CONTEXT_MESSAGE
  }
}
