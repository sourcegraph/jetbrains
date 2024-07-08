package com.sourcegraph.cody.agent.protocol

data class TestingDiagnosticsResult(
    val diagnostics: List<ProtocolDiagnostic>,
)
