package com.sourcegraph.cody.agent.protocol

data class ProtocolCommand(
    val title: String,
    val command: String,
    val tooltip: String? = null,
    val arguments: List<*>
)
