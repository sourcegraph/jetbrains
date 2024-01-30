package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState

class MessageState : BaseState() {

  var text: String? by string()
  var speaker by enum<SpeakerState>()
  // todo var contextFiles by list<String>()

  enum class SpeakerState {

    HUMAN,
    ASSISTANT
  }
}
