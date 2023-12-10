package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState

class HistoryState : BaseState() {

    var activeChatId by string()
    var chats by list<HistoryChatState>()

}
