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
        assertEquals(panel.itemSize(), 1)
        assertEquals(panel.itemTextAt(0), "This should be a title!")
    }

    fun `test default title when human messages are missing`() {
        val history = HistoryService.getInstance()
        history.startChat()
        history.addChatMessage(ChatMessage(ASSISTANT, ""))
        history.addChatMessage(ChatMessage(ASSISTANT, ""))

        val panel = HistoryPanel()
        assertEquals(panel.itemSize(), 1)
        assertEquals(panel.itemTextAt(0), "New chat")
    }

    fun `test multiple chats items are visible`() {
        val history = HistoryService.getInstance()
        history.startChat()
        history.startChat()

        val panel = HistoryPanel()
        assertEquals(panel.itemSize(), 2)
    }

    fun `test panel is auto-refreshing after change in state`() {
        val panel = HistoryPanel()
        assertEquals(panel.itemSize(), 0)
        HistoryService.getInstance().startChat()
        HistoryService.getInstance().startChat()
        assertEquals(panel.itemSize(), 2)
    }

    private fun HistoryPanel.itemSize() =
            getComponent().model.size

    private fun HistoryPanel.itemTextAt(idx: Int) =
            getComponent().model.getElementAt(idx).toString()


}
