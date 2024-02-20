package com.sourcegraph.cody.agent.protocol

data class ProtocolCommand(
    val title: TitleParams? = null,
    val command: String? = null,
    val tooltip: String? = null,
    // First argument is always task ID (string).
    val arguments: List<*>,
)
