package com.sourcegraph.cody.agent.protocol

data class ProtocolDiagnostic(
    val location: GetFoldingRangeParams,
    val message: String,
    val severity: String, // TODO use enum?
    val code: String? = null,
    val source: String? = null,
    val relatedInformation: List<ProtocolRelatedInformationDiagnostic>? = null,
)
