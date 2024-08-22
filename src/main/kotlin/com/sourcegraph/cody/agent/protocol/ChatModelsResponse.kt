package com.sourcegraph.cody.agent.protocol

data class ChatModelsResponse(val models: List<ChatModelProvider>) {
  data class ChatModelProvider(
      val provider: String?,
      val title: String?,
      val model: String,
      val tags: MutableList<String>? = mutableListOf(),
      val usage: MutableList<String>? = mutableListOf(),
      @Deprecated("No longer provided by agent") val default: Boolean = false,
      @Deprecated("No longer provided by agent") val codyProOnly: Boolean = false,
      @Deprecated("No longer provided by agent") val deprecated: Boolean = false
  )
}
