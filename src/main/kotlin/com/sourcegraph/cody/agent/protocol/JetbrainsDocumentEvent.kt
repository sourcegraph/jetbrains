package com.sourcegraph.cody.agent.protocol

data class JetbrainsDocumentEvent(
    val offset: Int,
    val oldLength: Int,
    val content: String,
)
