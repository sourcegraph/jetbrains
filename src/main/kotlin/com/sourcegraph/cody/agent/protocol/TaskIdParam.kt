package com.sourcegraph.cody.agent.protocol

data class TaskIdParam(val id: String, val range: Range? = null)
