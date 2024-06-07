package com.sourcegraph.cody

import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.edit.DocumentCodeTest
import com.sourcegraph.cody.util.CodyIntegrationTextFixture
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(DocumentCodeTest::class)
class AllSuites {
  companion object {
    @AfterClass
    @JvmStatic
    internal fun tearDown() {
      CodyAgentService.withAgent(CodyIntegrationTextFixture.myProject!!) { agent ->
        val errors = agent.server.testingRequestErrors().get()
        val missingRecordings = errors.filter { it.error?.contains("`recordIfMissing` is") == true }
        missingRecordings.forEach { missing ->
          System.err.println("Missing recording: ${missing.error}")
        }

        agent.server
            .shutdown()
            .get(CodyIntegrationTextFixture.ASYNC_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        agent.server.exit()
      }
    }
  }
}
