package com.sourcegraph.cody.agent.protocol

import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.agent.protocol_generated.Model
import javax.swing.Icon

data class ChatModelsResponse(val models: List<ChatModelProvider>) {
  data class ChatModelProvider(private val model: Model) {
    val provider: String? = model.provider
    val title: String? = model.title
    val id: String = model.id
    val tags: MutableList<String> = model.tags.map { it.toString() }.toMutableList()
    val usage: MutableList<String> = model.usage.map { it.toString() }.toMutableList()
    @Deprecated("No longer provided by agent") val default: Boolean = false
    @Deprecated("No longer provided by agent") val codyProOnly: Boolean = false
    @Deprecated("No longer provided by agent") val deprecated: Boolean = false

    fun getIcon(): Icon? =
        when (provider) {
          "Anthropic" -> Icons.LLM.Anthropic
          "OpenAI" -> Icons.LLM.OpenAI
          "Mistral" -> Icons.LLM.Mistral
          "Google" -> Icons.LLM.Google
          "Ollama" -> Icons.LLM.Ollama
          else -> null
        }

    fun displayName(): String = buildString {
      if (title == null) {
        if (id.isNotBlank()) {
          append(model)
        } else {
          append("Default")
        }
      } else {
        append(title)
        provider?.let { append(" by $provider") }
      }
    }

    fun isCodyProOnly(): Boolean = tags?.contains("pro") ?: codyProOnly

    fun isDeprecated(): Boolean = tags?.contains("deprecated") ?: deprecated
  }
}
