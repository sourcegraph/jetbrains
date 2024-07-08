package com.sourcegraph.cody.agent.protocol

// FIXME Apparently this has been renamed to ProtocolLocation on the agent side
data class GetFoldingRangeParams(val uri: String, val range: Range)
