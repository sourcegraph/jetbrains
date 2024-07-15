package com.sourcegraph.cody.agent.protocol

import com.sourcegraph.cody.agent.protocol_generated.Range

data class EditTask(
    val id: String,
    val state: CodyTaskState,
    val selectionRange: Range,
    val instruction: String? = null
)
