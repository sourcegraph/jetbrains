@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")

package com.sourcegraph.cody.agent.protocol

data class TitleParams(
    val text: String,
    val icons: List<IconsParams>,
)
