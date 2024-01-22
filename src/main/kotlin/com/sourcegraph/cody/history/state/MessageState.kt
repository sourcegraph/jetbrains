package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.sourcegraph.cody.agent.protocol.Speaker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageState : BaseState() {

  var text: String? by string()
  var speaker by enum<Speaker>()
  var contextFiles by list<String>()
  var date: String? by string()

  fun setDateTime(dateTime: LocalDateTime) {
    date = dateTime.format(DATE_FORMAT)
  }

  fun getDateTime(): LocalDateTime {
    return LocalDateTime.parse(date, DATE_FORMAT)
  }

  companion object {

    private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME
  }
}
