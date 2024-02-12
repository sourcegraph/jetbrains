package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Tag("chat")
class ChatState : BaseState() {

  @get:OptionTag(tag = "internalId", nameAttribute = "") var internalId: String? by string()

  @get:OptionTag(tag = "messages", nameAttribute = "")
  var messages: MutableList<MessageState> by list()

  @get:OptionTag(tag = "updatedAt", nameAttribute = "") var updatedAt: String? by string()

  @get:OptionTag(tag = "model", nameAttribute = "") var model: String? by string()

  @get:OptionTag(tag = "accountId", nameAttribute = "") var accountId: String? by string()

  @get:OptionTag(tag = "enhancedContext", nameAttribute = "")
  var enhancedContext: EnhancedContextState? by property()

  fun title(): String? = messages.firstOrNull()?.text?.take(48)

  fun setUpdatedTimeAt(date: LocalDateTime) {
    updatedAt = date.format(DATE_FORMAT)
  }

  fun getUpdatedTimeAt(): LocalDateTime {
    if (updatedAt == null) return LocalDateTime.now()
    return LocalDateTime.parse(updatedAt, DATE_FORMAT)
  }

  companion object {

    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun create(accountId: String?, internalId: String): ChatState {
      val chat = ChatState()
      chat.accountId = accountId
      chat.internalId = internalId
      return chat
    }
  }
}
