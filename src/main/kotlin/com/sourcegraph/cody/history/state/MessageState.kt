package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageState : BaseState() {

//  var messageId: String? by string()
  var text: String? by string()
  var speaker by enum<SpeakerState>()
//  var contextFiles by list<String>()
//  var createdAt: String? by string()


  enum class SpeakerState {

    HUMAN, ASSISTANT

  }


}
