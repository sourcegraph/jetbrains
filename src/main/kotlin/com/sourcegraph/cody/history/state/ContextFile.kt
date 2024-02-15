package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag

@Tag("contextFile")
class ContextFile : BaseState() {
  @get:OptionTag(tag = "uri", nameAttribute = "") var uri: String? by string()
}
