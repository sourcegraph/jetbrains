package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ChatState : BaseState() {

  var internalId by string()
  var messages by list<MessageState>()
  var updatedAt by string()

  fun title() = messages.first().text!!

  fun setUpdatedTimeAt(date: LocalDateTime) {
    updatedAt = date.format(DATE_FORMAT)
  }

  fun getUpdatedTimeAt(): LocalDateTime =
    LocalDateTime.parse(updatedAt, DATE_FORMAT)

  companion object {

    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
  }

}
