package com.sourcegraph.cody.agent.protocol

data class TextDocumentContentChangeEvent(
    val rangeOffset: Int,
    val rangeLength: Int,
    val text: String,
)
