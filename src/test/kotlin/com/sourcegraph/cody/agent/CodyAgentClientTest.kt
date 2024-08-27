package com.sourcegraph.cody.agent

import com.sourcegraph.cody.agent.protocol.CommitMessageParams
import com.sourcegraph.cody.agent.protocol.CommitMessageResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CodyAgentClientTest {

    @Test
    fun testGenerateCommitMessage() {
        val client = CodyAgentClient()
        val params = CommitMessageParams(
            filePath = "src/main/java/com/sourcegraph/cody/agent/CodyAgentClient.kt",
            diff = "diff --git a/src/main/java/com/sourcegraph/cody/agent/CodyAgentClient.kt b/src/main/java/com/sourcegraph/cody/agent/CodyAgentClient.kt\n" +
                    "index 83db48f..e6b0e5b 100644\n" +
                    "--- a/src/main/java/com/sourcegraph/cody/agent/CodyAgentClient.kt\n" +
                    "+++ b/src/main/java/com/sourcegraph/cody/agent/CodyAgentClient.kt\n" +
                    "@@ -1,6 +1,6 @@\n" +
                    " package com.sourcegraph.cody.agent\n" +
                    "\n" +
                    " import com.sourcegraph.cody.agent.protocol.CommitMessageParams\n" +
                    " import com.sourcegraph.cody.agent.protocol.CommitMessageResult\n" +
                    " import com.google.gson.Gson\n" +
                    " import java.net.HttpURLConnection\n" +
                    " import java.net.URL\n",
            template = "feat: Implemented new feature"
        )
        val result = client.generateCommitMessage(params)
        assertEquals("feat: Implemented new feature", result.commitMessage)
        assertEquals("Implemented new feature", result.prTitle)
        assertEquals("This PR implements a new feature.", result.prDescription)
    }
}
