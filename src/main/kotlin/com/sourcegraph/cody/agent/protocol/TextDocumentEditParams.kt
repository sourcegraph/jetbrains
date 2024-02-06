package com.sourcegraph.cody.agent.protocol

data class TextDocumentEditParams(
    // TODO: Should we include a task id?
    // Otherwise, the agent can pretty much write at will to any open file.
    val uri: String,
    val edits: List<TextEdit>,
    val options: TextDocumentEditOptions? = null
)
