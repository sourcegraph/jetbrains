package com.sourcegraph.cody.agent.protocol

import com.google.gson.Gson

data class CommitMessageParams(
    val filePath: String,
    val diff: String,
    val template: String
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }
}
