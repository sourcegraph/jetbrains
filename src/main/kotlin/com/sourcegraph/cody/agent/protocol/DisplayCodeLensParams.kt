package com.sourcegraph.cody.agent.protocol

data class DisplayCodeLensParams(val uri: String, val codeLenses: List<ProtocolCodeLens>)
