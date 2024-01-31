package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag

class HistoryState : BaseState() {

  @get:OptionTag(tag = "chats", nameAttribute = "")
  var chats by list<ChatState>()

}
