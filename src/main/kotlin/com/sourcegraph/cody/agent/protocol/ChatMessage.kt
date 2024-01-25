package com.sourcegraph.cody.agent.protocol

import java.time.OffsetDateTime

data class ChatError(
    val kind: String? = null,
    val name: String,
    val message: String,
    val retryAfter: String? = null,
    val limit: Int? = null,
    val userMessage: String? = null,
    val retryAfterDate: OffsetDateTime? = null,
    val retryMessage: String? = null,
    val feature: String? = null,
    val upgradeIsAvailable: Boolean? = null,
) {
  fun toRateLimitError(): RateLimitError? {
    if (this.upgradeIsAvailable == null) {
      return null
    }
    return RateLimitError(
        upgradeIsAvailable = this.upgradeIsAvailable,
        limit = this.limit,
    )
  }
}

data class ChatMessage(
    override val speaker: Speaker,
    override val text: String?,
    val displayText: String? = null,
    val contextFiles: List<ContextFile>? = null,
    val error: ChatError? = null,
    // `id` is used to identify if updates are sent for the same message
    // If none is provided we are just assigning new UUID to give the message unique identity
    // (e.g. in case of messages created at once by human)
    val id: String = java.util.UUID.randomUUID().toString()
) : Message {
  fun actualMessage(): String = displayText ?: text ?: ""
}
