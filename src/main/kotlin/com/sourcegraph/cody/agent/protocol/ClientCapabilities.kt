package com.sourcegraph.cody.agent.protocol

data class ClientCapabilities(
    var completions: String? = null,
    var chat: String? = null,
    var git: String? = null,
    var progressBars: String? = null,
    var edit: String? = null,
    var editWorkspace: String? = null,
    var codeLenses: String? = null,
    // 'lenses' for code lenses, must have codeLenses = 'enabled'
    var fixupControls: String? = null,
)
