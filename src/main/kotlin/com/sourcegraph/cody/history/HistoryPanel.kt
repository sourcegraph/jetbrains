package com.sourcegraph.cody.history

import com.intellij.ui.CollectionListModel

class HistoryPanel(private val onChange: (id: String) -> Unit = {}) {

  private val listComponent =
      HistoryList(
          onSelected = { item ->
            HistoryService.getInstance().state.activeChatId = item.id
            onChange(item.id)
          })

  init {
    HistoryService.getInstance().addMessageListener { refreshItems() }
    refreshItems()
  }

  fun getComponent() = listComponent

  private fun refreshItems() {
    val entries =
        HistoryService.getInstance()
            .state
            .chats
            .map {
              HistoryListItem(
                  id = it.id!!,
                  title = it.getLastHumanMessage() ?: "New chat",
                  lastUpdated = it.lastUpdatedAsDate())
            }
            .sortedByDescending { it.lastUpdated }
    listComponent.model = CollectionListModel(entries)
  }
}
