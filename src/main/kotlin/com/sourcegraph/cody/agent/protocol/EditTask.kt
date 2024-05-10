package com.sourcegraph.cody.agent.protocol

data class EditTask(
    val id: String,
    val state: CodyTaskState,
    val error: CodyError? = null,
    val selectionRange: Range,
    val instruction: String? = null
)
