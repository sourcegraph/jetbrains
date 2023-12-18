package com.sourcegraph.cody.history

import com.intellij.ui.components.JBViewport
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
    history.startChat()
    history.startChat()

    val panel = HistoryPanel()
    assertEquals(panel.numberOfChats(), 2)
  }

  fun `test panel visuals are refreshed after change in state`() {
    val panel = HistoryPanel()
    assertEquals(panel.numberOfChats(), 0)
    HistoryService.getInstance().startChat()
    HistoryService.getInstance().startChat()

    assertEquals(panel.numberOfChats(), 2)
  }

  private fun HistoryPanel.numberOfChats() = selectChatList().model.size

  private fun HistoryPanel.chatTitleAt(index: Int) =
      selectChatList().model.getElementAt(index).toString()

  private fun HistoryPanel.selectChatList() =
      (asScrollablePanel().components[0] as JBViewport).components[0] as HistoryList
}
