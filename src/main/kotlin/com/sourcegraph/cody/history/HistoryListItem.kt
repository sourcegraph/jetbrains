package com.sourcegraph.cody.history

import java.time.LocalDateTime

class HistoryListItem(val id: String, val title: String, val lastUpdated: LocalDateTime) {

  override fun toString() = title // this is displayed
}
