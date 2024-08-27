package com.sourcegraph.cody.agent

import com.sourcegraph.cody.agent.protocol.CommitMessageParams
import com.sourcegraph.cody.agent.protocol.CommitMessageResult
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

class CodyAgentClient {

    fun generateCommitMessage(params: CommitMessageParams): CommitMessageResult {
        val url = URL("http://localhost:8080/generateCommitMessage")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val jsonInputString = params.toJson()
        connection.outputStream.use { os ->
            val input = jsonInputString.toByteArray()
            os.write(input, 0, input.size)
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return Gson().fromJson(response, CommitMessageResult::class.java)
    }
}
