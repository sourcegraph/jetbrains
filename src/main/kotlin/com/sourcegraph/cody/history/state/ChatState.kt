package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Tag("chat")
class ChatState : BaseState() {

  @get:OptionTag(tag = "internalId", nameAttribute = "")
  var internalId by string()

  @get:OptionTag(tag = "messages", nameAttribute = "")
  var messages by list<MessageState>()

  @get:OptionTag(tag = "updatedAt", nameAttribute = "")
  var updatedAt by string()

  fun title() = messages.first().text!!

  fun setUpdatedTimeAt(date: LocalDateTime) {
    updatedAt = date.format(DATE_FORMAT)
  }

  fun getUpdatedTimeAt(): LocalDateTime = LocalDateTime.parse(updatedAt, DATE_FORMAT)

  companion object {

    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
  }
}
