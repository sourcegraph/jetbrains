package com.sourcegraph.cody.agent.protocol

data class ProtocolCodeLens(
    val range: Range,
    val command: ProtocolCommand? = null,
    val isResolved: Boolean
)
