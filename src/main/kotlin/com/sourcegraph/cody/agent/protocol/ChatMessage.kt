package com.sourcegraph.cody.agent.protocol

import com.sourcegraph.cody.commands.CommandId
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
    val speaker: Speaker,
    val source: Source?,
    val text: String?,
    val displayText: String? = null,
    val contextFiles: List<ContextFile>? = null,
    val error: ChatError? = null
) {
  companion object {
    val sourceToCommandId = CommandId.values().associateBy { it.source }
  }

  fun actualMessage(): String = sourceToCommandId[source]?.displayName ?: displayText ?: text ?: ""
}
