/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")

package com.sourcegraph.cody.agent.protocol_generated

data class RemoteRepo_ListParams(
    val query: String? = null,
    val first: Int,
    val afterId: String? = null,
)
