package com.sourcegraph.cody

import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.edit.DocumentCodeTest
import com.sourcegraph.cody.util.CodyIntegrationTextFixture
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

// We need a single tearDown() method running after all tests are complete,
// so agent can be closed gracefully and recordings can be saved properly.
// Due to the limitations of JUnit 4 this can be done only using SuiteClasses and AfterClass.
// Multiple recording files can be used, but each should have its own suite with tearDown() method
// nad define unique CODY_RECORDING_NAME.
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
