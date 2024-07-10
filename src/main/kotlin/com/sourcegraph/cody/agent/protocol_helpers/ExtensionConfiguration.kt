package com.sourcegraph.cody.agent.protocol_helpers

import com.sourcegraph.cody.agent.protocol_generated.ExtensionConfiguration as ProtocolExtensionConfiguration

data class ExtensionConfiguration(
    val anonymousUserID: String?,
    val serverEndpoint: String,
    val proxy: String? = null,
    val accessToken: String,
    val customHeaders: Map<String, String> = emptyMap(),
    val autocompleteAdvancedProvider: String? = null,
    val debug: Boolean? = false,
    val verboseDebug: Boolean? = false,
    val codebase: String? = null,
    val customConfiguration: Map<String, String> = emptyMap()
)

fun ExtensionConfiguration.toProtocol(): ProtocolExtensionConfiguration =
    ProtocolExtensionConfiguration(
        anonymousUserID = anonymousUserID,
        serverEndpoint = serverEndpoint,
        proxy = proxy,
        accessToken = accessToken,
        customHeaders = customHeaders,
        autocompleteAdvancedProvider = autocompleteAdvancedProvider,
        debug = debug,
        verboseDebug = verboseDebug,
        codebase = codebase,
        customConfiguration = customConfiguration)
