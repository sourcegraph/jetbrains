/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")

package com.sourcegraph.cody.agent.protocol_generated

data class CodyLLMSiteConfiguration(
    val chatModel: String? = null,
    val chatModelMaxTokens: Int? = null,
    val fastChatModel: String? = null,
    val fastChatModelMaxTokens: Int? = null,
    val completionModel: String? = null,
    val completionModelMaxTokens: Int? = null,
    val provider: String? = null,
    val smartContextWindow: Boolean? = null,
)
