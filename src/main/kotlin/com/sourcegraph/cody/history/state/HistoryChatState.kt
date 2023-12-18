package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.sourcegraph.cody.agent.protocol.Speaker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class HistoryChatState : BaseState() {

  var id by string()
  var messages by list<HistoryChatMessageState>()
  var lastUpdated by string()

  fun getLastHumanMessage(): String? = messages.firstOrNull { it.speaker == Speaker.HUMAN }?.text

  fun updateLastUpdated() {
    lastUpdated = getFormattedNow()
  }

  fun lastUpdatedAsDate(): LocalDateTime = LocalDateTime.parse(lastUpdated!!, DATE_FORMAT)

  companion object {

    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun newEmpty() =
        HistoryChatState().apply {
          id = UUID.randomUUID().toString()
          lastUpdated = getFormattedNow()
        }

    private fun getFormattedNow() = LocalDateTime.now().format(DATE_FORMAT)
  }
}
