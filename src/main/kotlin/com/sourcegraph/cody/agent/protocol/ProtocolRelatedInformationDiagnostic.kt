package com.sourcegraph.cody.agent.protocol

data class ProtocolRelatedInformationDiagnostic(
    val location: GetFoldingRangeParams,
    val message: String
)
