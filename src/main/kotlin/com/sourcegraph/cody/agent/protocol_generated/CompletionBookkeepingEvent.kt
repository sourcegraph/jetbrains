/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")

package com.sourcegraph.cody.agent.protocol_generated

data class CompletionBookkeepingEvent(
    val id: CompletionLogID,
    val startedAt: Int,
    val networkRequestStartedAt: Int? = null,
    val startLoggedAt: Int? = null,
    val loadedAt: Int? = null,
    val suggestedAt: Int? = null,
    val suggestionLoggedAt: Int? = null,
    val suggestionAnalyticsLoggedAt: Int? = null,
    val acceptedAt: Int? = null,
    val items: List<CompletionItemInfo>,
    val loggedPartialAcceptedLength: Int,
)
