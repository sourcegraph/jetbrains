package com.sourcegraph.cody.agent.protocol

data class RetryEditParams(
    val id: String,
    val instruction: String,
    val model: String,
    val mode: String,
    val range: Range
)
