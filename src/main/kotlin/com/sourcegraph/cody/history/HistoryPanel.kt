package com.sourcegraph.cody.history

import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBScrollPane

class HistoryPanel(private val onChanged: (id: String) -> Unit = {}) {

  private val listComponent =
      HistoryList(
          onSelect = { item ->
            HistoryService.getInstance().state.activeChatId = item.chatId
            onChanged(item.chatId)
          })

  init {
    HistoryService.getInstance().addNewMessageListener { refreshItems() }
    refreshItems()
  }

  fun asScrollablePanel() =
      JBScrollPane(
              listComponent,
              JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
              JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
          .also { it.border = null }

  private fun refreshItems() {
    val entries =
        HistoryService.getInstance()
            .state
            .chats
            .map {
              HistoryListItem(
                  chatId = it.id!!,
                  title = it.getLastHumanMessage() ?: "New chat",
                  lastUpdated = it.lastUpdatedAsDate())
            }
            .sortedByDescending { it.lastUpdated }
    listComponent.model = CollectionListModel(entries)
  }
}
