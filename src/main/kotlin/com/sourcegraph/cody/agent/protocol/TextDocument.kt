package com.sourcegraph.cody.agent.protocol

import java.net.URI

data class TextDocument
// JvmOverloads needed until CodyAgentFocusListener
// and CodyFileEditorListener are converted to Kotlin.
@JvmOverloads
constructor(
    var uri: URI,
    var content: String? = null,
    var selection: Range? = null,
) {
  @JvmOverloads
  constructor(
      filePath: String,
      content: String? = null,
      selection: Range? = null,
  ) : this(URI("file://$filePath"), content, selection)
}
