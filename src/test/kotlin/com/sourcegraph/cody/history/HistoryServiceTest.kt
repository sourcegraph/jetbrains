package com.sourcegraph.cody.history

class HistoryServiceTest : BaseHistoryTest() {

    fun `test empty history has no active chat by default`() {
        val history = HistoryService.getInstance()
        assertNull(history.state.activeChatId)
    }

    fun `test started chat is set as active`() {
        val history = HistoryService.getInstance()
        val id = history.startChat()
        assertEquals(id, history.state.activeChatId)
    }

    fun `test latest chat is marked as active`() {
        val history = HistoryService.getInstance()
        history.startChat()
        history.startChat()
        val lastId = history.startChat()
        assertEquals(lastId, history.state.activeChatId)
    }

}