package com.sourcegraph.cody.agent.protocol_helpers

import com.sourcegraph.cody.agent.protocol_generated.ClientInfo as ProtocolClientInfo

data class ClientInfo(
    var version: String,
    var ideVersion: String,
    var workspaceRootUri: String? = null,
    var extensionConfiguration: ExtensionConfiguration? = null,
    var capabilities: ClientCapabilities? = null,
) {
  val name = "JetBrains"
}

fun ClientInfo.toProtocol(): ProtocolClientInfo {
  return ProtocolClientInfo(
      name = this.name,
      version = this.version,
      ideVersion = this.ideVersion,
      workspaceRootUri =
          this.workspaceRootUri ?: throw IllegalArgumentException("workspaceRootUri is required"),
      extensionConfiguration = this.extensionConfiguration?.toProtocol(),
      capabilities = this.capabilities?.toProtocol(),
  )
}
