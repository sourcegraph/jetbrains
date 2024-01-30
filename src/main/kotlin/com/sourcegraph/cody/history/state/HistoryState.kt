package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState

class HistoryState : BaseState() {
  var chats by list<ChatState>()
}
