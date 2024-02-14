package com.sourcegraph.cody.agent.protocol_extensions

import com.sourcegraph.cody.protocol_generated.ProtocolTextDocument
import com.sourcegraph.cody.protocol_generated.Range
import java.nio.file.Paths

object TextDocumentBuilder {
  @JvmStatic
  @JvmOverloads
  fun fromPath(
      path: String,
      content: String? = null,
      selection: Range? = null
  ): ProtocolTextDocument {
    val uri = Paths.get(path).toUri().toString()
    val rfc3986Uri = Rfc3986UriEncoder.encode(uri)
    return ProtocolTextDocument(uri = rfc3986Uri, content = content, selection = selection)
  }
}
