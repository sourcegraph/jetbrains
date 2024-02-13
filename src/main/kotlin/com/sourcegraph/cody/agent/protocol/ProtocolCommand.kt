package com.sourcegraph.cody.agent.protocol

data class ProtocolCommand(
    val title: String,
    val command: String,
    val tooltip: String? = null,
    // First element is task ID (string).
    val arguments: List<*>
)
