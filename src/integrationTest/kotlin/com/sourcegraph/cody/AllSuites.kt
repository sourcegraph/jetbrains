package com.sourcegraph.cody

import com.intellij.openapi.diagnostic.Logger
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.edit.DocumentCodeTest
import com.sourcegraph.cody.util.CodyIntegrationTestFixture
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * We need a single tearDown() method running after all tests are complete, so agent can be closed
 * gracefully and recordings can be saved properly.
 *
 * Due to the limitations of JUnit 4 this can be done only using SuiteClasses and AfterClass, and we
 * are forced to use JUnit 4 because IntelliJ testing classes like `BasePlatformTestCase` are based
 * on that version. JUnit 4 jar is part of the ideaIC package. We will upgrade to JUnit 5
 * automatically after the platform version bump.
 *
 * Multiple recording files can be used, but each should have its own suite with tearDown() method
 * and define a unique CODY_RECORDING_NAME.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(DocumentCodeTest::class, DocumentSynchronizationTest::class)
class AllSuites {
  companion object {
    private val logger = Logger.getInstance(AllSuites::class.java)

    @AfterClass
    @JvmStatic
    internal fun tearDown() {
      val project = CodyIntegrationTestFixture.myProject
      // Can happen if a test or fixture introduces a bug, and it makes it hard to read test output
      // if we are throwing an NPE.
      if (project == null) {
        logger.warn("No project found - unable to shut down agent gracefully.")
        return
      }
      CodyAgentService.withAgent(project) { agent ->
        val errors = agent.server.testingRequestErrors().get()
        // We extract polly.js errors to notify users about the missing recordings, if any
        val missingRecordings = errors.filter { it.error?.contains("`recordIfMissing` is") == true }
        missingRecordings.forEach { missing ->
          System.err.println(
              """Recording is missing: ${missing.error}
                |
                |------------------------------------------------------------------------------------------
                |To fix this problem please run `./gradlew :recordingIntegrationTest`.
                |You need to export access tokens first, using script from the `sourcegraph/cody` repository:
                |`agent/scripts/export-cody-http-recording-tokens.sh`
                |------------------------------------------------------------------------------------------
              """
                  .trimMargin())
        }

        agent.server
            .shutdown()
            .get(CodyIntegrationTestFixture.ASYNC_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        agent.server.exit()
      }
    }
  }
}
