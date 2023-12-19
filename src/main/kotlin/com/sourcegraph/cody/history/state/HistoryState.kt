package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState

class HistoryState : BaseState() {

  var activeChatId: ChatId? by string()
  var chats: MutableList<ChatState> by list()
}
