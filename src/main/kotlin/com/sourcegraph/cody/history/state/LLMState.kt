package com.sourcegraph.cody.history.state

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse

@Tag("llm")
class LLMState : BaseState() {
  @get:OptionTag(tag = "model", nameAttribute = "") var model: String? by string()

  @get:OptionTag(tag = "title", nameAttribute = "") var title: String? by string()

  @get:OptionTag(tag = "provider", nameAttribute = "") var provider: String? by string()

  @get:OptionTag(tag = "tags", nameAttribute = "") var tags: MutableList<String> by list()

  @get:OptionTag(tag = "usage", nameAttribute = "") var usage: MutableList<String> by list()

  companion object {
    fun fromChatModel(chatModelProvider: ChatModelsResponse.ChatModelProvider): LLMState {
      return LLMState().also {
        it.model = chatModelProvider.model
        it.title = chatModelProvider.title
        it.provider = chatModelProvider.provider
        it.tags = chatModelProvider.tags ?: mutableListOf()
        it.usage = chatModelProvider.usage ?: mutableListOf()
      }
    }
  }
}
