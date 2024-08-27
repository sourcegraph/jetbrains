package com.sourcegraph.cody.agent.protocol

import com.google.gson.Gson

data class CommitMessageResult(
    val commitMessage: String,
    val prTitle: String,
    val prDescription: String
) {
    fun toJson(): String {
        return Gson().toJson(this)
    }
}
