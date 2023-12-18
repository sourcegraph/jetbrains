package com.sourcegraph.cody.history

import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker.ASSISTANT
import com.sourcegraph.cody.agent.protocol.Speaker.HUMAN

class HistoryPanelTest : BaseHistoryTest() {

  fun `test title is derived from first human message`() {
    val history = HistoryService.getInstance()
    history.startChat()
    history.addChatMessage(ChatMessage(ASSISTANT, "Hello! I'm Cody."))
    history.addChatMessage(ChatMessage(HUMAN, "This should be a title!"))
    history.addChatMessage(ChatMessage(ASSISTANT, "Cody response."))
    history.addChatMessage(ChatMessage(HUMAN, "My second question."))
    history.addChatMessage(ChatMessage(ASSISTANT, "Second response."))

    val panel = HistoryPanel()
    assertEquals(panel.numberOfChats(), 1)
    assertEquals(panel.chatTitleAt(0), "This should be a title!")
  }

  fun `test default title when human messages are missing`() {
    val history = HistoryService.getInstance()
    history.startChat()
    history.addChatMessage(ChatMessage(ASSISTANT, ""))
    history.addChatMessage(ChatMessage(ASSISTANT, ""))

    val panel = HistoryPanel()
    assertEquals(panel.numberOfChats(), 1)
    assertEquals(panel.chatTitleAt(0), "New chat")
  }

  fun `test multiple chats items are visible`() {
    val history = HistoryService.getInstance()
    repeat(2) { history.startChat() }

    val panel = HistoryPanel()
    assertEquals(panel.numberOfChats(), 2)
  }

  fun `test panel is auto-refreshing after change in state`() {
    val panel = HistoryPanel()
    assertEquals(panel.numberOfChats(), 0)
    repeat(2) { HistoryService.getInstance().startChat() }

    assertEquals(panel.numberOfChats(), 2)
  }

  private fun HistoryPanel.numberOfChats() =
      (getScrollableList().components[0] as HistoryList).model.size

  private fun HistoryPanel.chatTitleAt(index: Int) =
      ((getScrollableList().components[0] as HistoryList)).model.getElementAt(index).toString()
}
