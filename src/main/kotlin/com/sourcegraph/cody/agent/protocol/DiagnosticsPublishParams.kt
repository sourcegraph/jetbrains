package com.sourcegraph.cody.agent.protocol

data class DiagnosticsPublishParams(val diagnostics: List<ProtocolDiagnostic>)
