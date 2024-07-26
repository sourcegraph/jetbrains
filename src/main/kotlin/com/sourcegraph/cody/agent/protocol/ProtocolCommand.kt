@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")

package com.sourcegraph.cody.agent.protocol

data class ProtocolCommand(
    val title: TitleParams,
    val command: String,
    val tooltip: String? = null,
    val arguments: List<Any>? = null,
)
