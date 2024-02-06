package com.sourcegraph.cody.agent.protocol

import com.sourcegraph.cody.agent.ExtensionConfiguration
import java.net.URI

data class ClientInfo(
    var version: String,
    var workspaceRootUri: URI,
    var extensionConfiguration: ExtensionConfiguration? = null,
    var capabilities: ClientCapabilities? = null,
) {
  val name = "JetBrains"
}
