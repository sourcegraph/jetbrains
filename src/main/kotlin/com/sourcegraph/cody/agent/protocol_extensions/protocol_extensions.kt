@file:Suppress("unused")

package com.sourcegraph.cody.agent.protocol_extensions

import com.sourcegraph.cody.protocol_generated.ChatError
import com.sourcegraph.cody.protocol_generated.ChatMessage
import com.sourcegraph.cody.protocol_generated.ProtocolTextDocument
import com.sourcegraph.cody.protocol_generated.Range
import java.nio.file.Paths

fun protocolTextDocumentFromPath(
    path: String,
    content: String? = null,
    selection: Range? = null
): ProtocolTextDocument {
  val uri = Paths.get(path).toUri().toString()
  val rfc3986Uri = Rfc3986UriEncoder.encode(uri)
  return ProtocolTextDocument(uri = rfc3986Uri, content = content, selection = selection)
}

fun ChatError.toRateLimitError(): RateLimitError? {
  val upgradeIsAvailable = this.upgradeIsAvailable ?: return null
  return RateLimitError(
      upgradeIsAvailable = upgradeIsAvailable,
      limit = this.limit,
  )
}

fun ChatMessage.actualMessage(): String = this.displayText ?: this.text ?: ""
