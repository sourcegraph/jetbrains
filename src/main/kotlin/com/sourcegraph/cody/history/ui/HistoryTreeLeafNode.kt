package com.sourcegraph.cody.history.ui

import com.sourcegraph.cody.history.state.ChatState
import javax.swing.tree.DefaultMutableTreeNode

class HistoryTreeLeafNode(val chat: ChatState) : DefaultMutableTreeNode(chat, false) {

  fun getText() = chat.title()
}
