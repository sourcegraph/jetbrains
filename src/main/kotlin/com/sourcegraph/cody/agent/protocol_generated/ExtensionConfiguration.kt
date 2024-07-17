/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")

package com.sourcegraph.cody.agent.protocol_generated

data class ExtensionConfiguration(
    val serverEndpoint: String,
    val proxy: String? = null,
    val accessToken: String,
    val customHeaders: Map<String, String>,
    val anonymousUserID: String? = null,
    val autocompleteAdvancedProvider: String? = null,
    val autocompleteAdvancedModel: String? = null,
    val debug: Boolean? = null,
    val verboseDebug: Boolean? = null,
    val codebase: String? = null,
    val eventProperties: EventProperties? = null,
    val customConfiguration: Map<String, Any>? = null,
    val baseGlobalState: Map<String, Any>? = null,
)
