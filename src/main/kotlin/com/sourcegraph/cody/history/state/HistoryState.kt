package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState

class HistoryState : BaseState() {
  // todo check if we can append some fields without breaking historical chats
  // todo to ensure that we can perform migration / add fields in future
  
  var chats by list<ChatState>()
}
