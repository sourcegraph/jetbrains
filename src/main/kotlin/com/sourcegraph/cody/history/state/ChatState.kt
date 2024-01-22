package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState

class ChatState : BaseState() {

  var panelId: String? by string()
  var replyChatId: String? by string()
  var messages by list<MessageState>()

  fun title() = messages.first().text!!

  fun latestMessage() = messages.minOf { it.getDateTime() }
}
