package com.sourcegraph.cody.agent

import com.sourcegraph.cody.agent.protocol.CommitMessageParams
import com.sourcegraph.cody.agent.protocol.CommitMessageResult

class CodyAgentService {

    private val client = CodyAgentClient()

    fun generateCommitMessage(params: CommitMessageParams): CommitMessageResult {
        return client.generateCommitMessage(params)
    }
}
