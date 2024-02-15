package com.sourcegraph.cody.agent.protocol

data class ProtocolCodeLens(
    val range: Range,
    val command: ProtocolCommand? = null,
    // True if a command is associated with the code lens.
    val isResolved: Boolean
)
