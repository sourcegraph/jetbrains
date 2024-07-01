package com.sourcegraph.cody.util

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.messages.Topic
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.config.CodyPersistentAccountsHost
import com.sourcegraph.cody.config.SourcegraphServerPath
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.edit.sessions.FixupSession
import com.sourcegraph.config.ConfigUtil
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
abstract class CodyIntegrationTestFixture : BasePlatformTestCase() {

  @JvmField @Rule var testName = TestName()

  override fun setUp() {
    super.setUp()
    val methodName =
        testName.methodName
            ?: throw IllegalStateException(
                "testName.methodName is null. Make sure the test has this rule set up correctly.")
    val method =
        this.javaClass.getMethod(methodName)
            ?: throw IllegalStateException(
                "No method with name $methodName found in ${this.javaClass.name}")
    if (method.isAnnotationPresent(TestFile::class.java)) {
      val testFile = method.getAnnotation(TestFile::class.java).value
      configureFixture(testFile)
    }
    checkInitialConditions()
    myProject = project
  }

  override fun tearDown() {
    try {
      FixupService.getInstance(myFixture.project).getActiveSession()?.apply {
        try {
          dispose()
        } catch (x: Exception) {
          logger.warn("Error shutting down session", x)
        }
      }
    } finally {
      super.tearDown()
    }
  }

  private fun configureFixture(testFile: String) {
    // If you don't specify this system property with this setting when running the tests,
    // the tests will fail, because IntelliJ will run them from the EDT, which can't block.
    // Setting this property invokes the tests from an executor pool thread, which lets us
    // block/wait on potentially long-running operations during the integration test.
    val policy = System.getProperty("idea.test.execution.policy")
    assertTrue(policy == "com.sourcegraph.cody.test.NonEdtIdeaTestExecutionPolicy")

    // This is wherever src/integrationTest/resources is on the box running the tests.
    val testResourcesDir = File(System.getProperty("test.resources.dir"))
    assertTrue(testResourcesDir.exists())

    // During test runs this is set by IntelliJ to a private temp folder.
    // We copy the test resources there manually, bypassing Gradle, which is picky.
    val testDataPath = Paths.get(getTestDataPath()).toFile()
    testResourcesDir.copyRecursively(testDataPath, overwrite = true)

    // This useful setting lets us tell the fixture to look where we copied them.
    myFixture.testDataPath = testDataPath.path

    // The file we pass to configureByFile must be relative to testDataPath.
    val sourcePath = Paths.get(testDataPath.path, testFile).toString()
    assertTrue(File(sourcePath).exists())
    myFixture.configureByFile(testFile)

    initCredentialsAndAgent()
    initCaretPosition()
  }

  override fun getTestDataPath(): String {
    if (project == null) {
      return super.getTestDataPath()
    }
    // During test runs this is set by IntelliJ to a private temp folder.
    // We pass it to the Agent during initialization.
    val workspaceRootUri = ConfigUtil.getWorkspaceRootPath(project)
    return Paths.get(workspaceRootUri.toString(), "src/").toString()
  }

  // Ideally we should call this method only once per recording session, but since we need a
  // `project` to be present it is currently hard to do with Junit 4.
  // Methods there are mostly idempotent though, so calling again for every test case should not
  // change anything.
  private fun initCredentialsAndAgent() {
    val credentials = TestingCredentials.dotcom
    CodyPersistentAccountsHost(project)
        .addAccount(
            SourcegraphServerPath.from(credentials.serverEndpoint, ""),
            login = "test_user",
            displayName = "Test User",
            token = credentials.token ?: credentials.redactedToken,
            id = "random-unique-testing-id-1337")

    assertNotNull(
        "Unable to start agent in a timely fashion!",
        CodyAgentService.getInstance(project)
            .startAgent(project)
            .completeOnTimeout(null, ASYNC_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .get())
  }

  protected open fun checkInitialConditions() {
    val project = myFixture.project

    // Check if the project is in dumb mode
    val isDumbMode = DumbService.getInstance(project).isDumb
    assertFalse("Project should not be in dumb mode", isDumbMode)

    // Check if the project is in LightEdit mode
    val isLightEditMode = LightEdit.owns(project)
    assertFalse("Project should not be in LightEdit mode", isLightEditMode)
  }

  protected fun createEditorContext(editor: Editor): DataContext {
    return (editor as? EditorEx)?.dataContext ?: DataContext.EMPTY_CONTEXT
  }

  // This provides a crude mechanism for specifying the caret position in the test file.
  private fun initCaretPosition() {
    runInEdtAndWait {
      val virtualFile = myFixture.file.virtualFile
      val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
      val caretToken = "[[caret]]"
      val caretIndex = document.text.indexOf(caretToken)

      if (caretIndex != -1) { // Remove caret token from doc
        WriteCommandAction.runWriteCommandAction(project) {
          document.deleteString(caretIndex, caretIndex + caretToken.length)
        }
        // Place the caret at the position where the token was found.
        myFixture.editor.caretModel.moveToOffset(caretIndex)
        // myFixture.editor.selectionModel.setSelection(caretIndex, caretIndex)
      } else {
        initSelectionRange()
      }
    }
  }

  // Provides  a mechanism to specify the selection range via [[start]] and [[end]].
  // The tokens are removed and the range is selected, notifying the Agent.
  private fun initSelectionRange() {
    runInEdtAndWait {
      val virtualFile = myFixture.file.virtualFile
      val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
      val startToken = "[[start]]"
      val endToken = "[[end]]"
      val start = document.text.indexOf(startToken)
      val end = document.text.indexOf(endToken)
      // Remove the tokens from the document.
      if (start != -1 && end != -1) {
        ApplicationManager.getApplication().runWriteAction {
          document.deleteString(start, start + startToken.length)
          document.deleteString(end, end + endToken.length)
        }
        myFixture.editor.selectionModel.setSelection(start, end)
      } else {
        logger.warn("No caret or selection range specified in test file.")
      }
    }
  }

  private fun triggerAction(actionId: String) {
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      EditorTestUtil.executeAction(myFixture.editor, actionId)
    }
  }

  protected fun activeSession(): FixupSession {
    assertActiveSession()
    return FixupService.getInstance(project).getActiveSession()!!
  }

  protected fun assertNoInlayShown() {
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertFalse(
          "Lens group inlay should NOT be displayed",
          myFixture.editor.inlayModel.hasBlockElements())
    }
  }

  protected fun assertInlayIsShown() {
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      assertTrue(
          "Lens group inlay should be displayed", myFixture.editor.inlayModel.hasBlockElements())
    }
  }

  protected fun assertNoActiveSession() {
    assertNull(
        "NO active session was expected", FixupService.getInstance(project).getActiveSession())
  }

  protected fun assertActiveSession() {
    assertNotNull(
        "Active session was expected", FixupService.getInstance(project).getActiveSession())
  }

  protected fun runAndWaitForNotifications(
      actionId: String,
      vararg topic: Topic<CodyInlineEditActionNotifier>
  ) {
    val futures = topic.associateWith { subscribeToTopic(it) }
    triggerAction(actionId)
    futures.forEach { (t, f) ->
      try {
        f.get()
      } catch (e: Exception) {
        assertTrue(
            "Error while awaiting ${t.displayName} notification: ${e.localizedMessage}", false)
      }
    }
  }

  // Returns a future that completes when the topic is published.
  private fun subscribeToTopic(
      topic: Topic<CodyInlineEditActionNotifier>,
  ): CompletableFuture<Void> {
    val future = CompletableFuture<Void>().orTimeout(ASYNC_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    project.messageBus
        .connect()
        .subscribe(
            topic,
            object : CodyInlineEditActionNotifier {
              override fun afterAction() {
                logger.warn("Notification sent for topic '${topic.displayName}'")
                future.complete(null)
              }
            })
    logger.warn("Subscribed to topic: $topic")
    return future
  }

  protected fun hasJavadocComment(text: String): Boolean {
    // TODO: Check for the exact contents once they are frozen.
    val javadocPattern = Pattern.compile("/\\*\\*.*?\\*/", Pattern.DOTALL)
    return javadocPattern.matcher(text).find()
  }

  companion object {
    private val logger = Logger.getInstance(CodyIntegrationTestFixture::class.java)

    const val ASYNC_WAIT_TIMEOUT_SECONDS = 10L
    var myProject: Project? = null
  }
}
