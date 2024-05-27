package com.sourcegraph.cody.edit

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.messages.Topic
import com.sourcegraph.cody.edit.sessions.FixupSession
import com.sourcegraph.cody.edit.widget.LensAction
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensLabel
import com.sourcegraph.cody.edit.widget.LensSpinner
import com.sourcegraph.config.ConfigUtil
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern
import org.mockito.Mockito.mock

class DocumentCodeTest : BasePlatformTestCase() {
  private val logger = Logger.getInstance(DocumentCodeTest::class.java)

  private lateinit var testDataPath: File

  override fun setUp() {
    super.setUp()
    configureFixture()
  }

  override fun tearDown() {
    try {
      FixupService.getInstance(myFixture.project).getActiveSession()?.apply {
        try {
          dismiss()
        } catch (x: Exception) {
          logger.warn("Error shutting down session", x)
        }
      }
      // Notify the Agent that all documents have been closed.
      val fileEditorManager = FileEditorManager.getInstance(myFixture.project)
      fileEditorManager.openFiles.forEach {
        // TODO: Check that this shows up in the trace.json file (textDocument/didClose).
        fileEditorManager.closeFile(it)
      }
      try {
        // TODO: This seemed to kill one of the tests.
        // testDataPath.deleteRecursively()
      } catch (x: Exception) {
        logger.warn("Error deleting test data", x)
      }
    } finally {
      super.tearDown()
    }
  }

  fun testGetsFoldingRanges() {
    val foldingRangeFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES)
    executeDocumentCodeAction()
    runInEdtAndWait {
      val rangeContext =
          try {
            foldingRangeFuture.get()
          } catch (t: TimeoutException) {
            fail("Timed out waiting for folding ranges")
            null
          }
      assertNotNull("Computed selection range should be non-null", rangeContext)
      // Ensure we were able to get the selection range.
      val selection = rangeContext!!.selectionRange
      assertNotNull("Selection should have been set", selection)
      // We set the selection range to whatever the protocol returns.
      // If a 0-width selection turns out to be reasonable we can adjust or remove this test.
      assertFalse("Selection range should not be zero-width", selection!!.start == selection.end)
      // A more robust check is to see if the selection "range" is just the caret position.
      // If so, then our fallback range somehow made the round trip, which is bad. The lenses will
      // go in the wrong places, etc.
      val document = myFixture.editor.document
      val startOffset = selection.start.toOffset(document)
      val endOffset = selection.end.toOffset(document)
      val caret = myFixture.editor.caretModel.primaryCaret.offset
      assertFalse(
          "Selection range should not equal the caret position",
          startOffset == caret && endOffset == caret)
    }
  }

  fun skip_testGetsWorkingGroupLens() {
    val future = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
    executeDocumentCodeAction()

    // Wait for the working group.
    val context = future.get()
    assertNotNull("Timed out waiting for working group lens", context)

    // The inlay should be up.
    assertTrue(
        "Lens group inlay should be displayed", myFixture.editor.inlayModel.hasBlockElements())

    // Lens group should match the expected structure.
    val lenses = context!!.session.lensGroup
    assertNotNull("Lens group should be displayed", lenses)

    val widgets = lenses!!.widgets
    assertEquals("Lens group should have 6 widgets", 6, widgets.size)
    assertTrue("Zeroth lens should be a spinner", widgets[0] is LensSpinner)
    assertTrue("First lens is space separator label", (widgets[1] as LensLabel).text == " ")
    assertTrue("Second lens is working label", (widgets[2] as LensLabel).text.contains("working"))
    assertTrue(
        "Third lens is separator label",
        (widgets[3] as LensLabel).text == LensGroupFactory.SEPARATOR)
    assertTrue("Fourth lens should be an action", widgets[4] is LensAction)
    assertTrue(
        "Fifth lens should be a label with a hotkey",
        (widgets[5] as LensLabel).text.matches(Regex(" \\(.+\\)")))
  }

  private fun awaitAcceptLensGroup(): CodyInlineEditActionNotifier.Context {
    val future = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ACCEPT_GROUP)
    executeDocumentCodeAction() // awaits sending command to Agent
    val context = future.get() // awaits the Accept group appearing
    assertNotNull("Timed out waiting for Accept group lens", context)
    val editor = myFixture.editor
    assertTrue("Lens group inlay should be displayed", editor.inlayModel.hasBlockElements())
    return context!!
  }

  fun skip_testShowsAcceptLens() {
    val context = awaitAcceptLensGroup()

    // Lens group should match the expected structure.
    val lenses = context.session.lensGroup
    assertNotNull("Lens group should be displayed", lenses)

    val widgets = lenses!!.widgets
    // There are 13 widgets as of the time of writing, but the UX could change, so check robustly.
    assertTrue("Lens group should have at least 4 widgets", widgets.size >= 4)
    assertNotNull(
        "Lens group should contain Accept action",
        widgets.find { widget ->
          widget is LensAction && widget.actionId == FixupSession.ACTION_ACCEPT
        })
    assertNotNull(
        "Lens group should contain Show Diff action",
        widgets.find { widget ->
          widget is LensAction && widget.actionId == FixupSession.ACTION_DIFF
        })
    assertNotNull(
        "Lens group should contain Show Undo action",
        widgets.find { widget ->
          widget is LensAction && widget.actionId == FixupSession.ACTION_UNDO
        })
    assertNotNull(
        "Lens group should contain Show Retry action",
        widgets.find { widget ->
          widget is LensAction && widget.actionId == FixupSession.ACTION_RETRY
        })

    // Make sure a doc comment was inserted.
    assertTrue(hasJavadocComment(myFixture.editor.document.text))

    // This avoids an error saying the spinner hasn't shut down, at the end of the test.
    runInEdtAndWait { Disposer.dispose(lenses) }
  }

  fun skip_testAccept() {
    val project = myFixture.project!!
    assertNull(FixupService.getInstance(project).getActiveSession())

    awaitAcceptLensGroup()
    assertTrue(myFixture.editor.inlayModel.hasBlockElements())
    assertNotNull(FixupService.getInstance(project).getActiveSession())

    val future = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_PERFORM_ACCEPT)
    triggerAction(FixupSession.ACTION_ACCEPT)

    val context = future.get()
    assertNotNull("Timed out waiting for Accept action to complete", context)

    assertFalse(myFixture.editor.inlayModel.hasBlockElements())
    assertNull(FixupService.getInstance(project).getActiveSession())
  }

  fun skip_testUndo() {
    val undoFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_PERFORM_UNDO)
    executeDocumentCodeAction()
    // The Accept/Retry/Undo group is now showing.
    triggerAction(FixupSession.ACTION_UNDO)

    val context = undoFuture.get()
    assertNotNull("Timed out waiting for Undo action", context)
  }

  private fun executeDocumentCodeAction() {
    assertFalse(myFixture.editor.inlayModel.hasBlockElements())
    // Execute the action and await the working group lens.
    runInEdtAndWait { EditorTestUtil.executeAction(myFixture.editor, "cody.documentCodeAction") }
  }

  private fun configureFixture() {
    // This is wherever src/integrationTest/resources is on the box running the tests.
    val testResourcesDir = File(System.getProperty("test.resources.dir"))
    assertTrue(testResourcesDir.exists())

    // During test runs this is set by IntelliJ to a private temp folder.
    // We pass it to the Agent during initialization.
    val workspaceRootUri = ConfigUtil.getWorkspaceRootPath(project)

    // We copy the test resources there manually, bypassing Gradle, which is picky.
    testDataPath = Paths.get(workspaceRootUri.toString(), "src/").toFile()
    testResourcesDir.copyRecursively(testDataPath, overwrite = true)

    // This useful setting lets us tell the fixture to look where we copied them.
    myFixture.testDataPath = testDataPath.path

    // The file we pass to configureByFile must be relative to testDataPath.
    val projectFile = "testProjects/documentCode/src/main/java/Foo.java"
    val sourcePath = Paths.get(testDataPath.path, projectFile).toString()
    assertTrue(File(sourcePath).exists())
    myFixture.configureByFile(projectFile)

    initCaretPosition()
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

  private fun subscribeToTopic(
      topic: Topic<CodyInlineEditActionNotifier>,
  ): CompletableFuture<CodyInlineEditActionNotifier.Context?> {
    val future =
        CompletableFuture<CodyInlineEditActionNotifier.Context?>()
            .completeOnTimeout(null, ASYNC_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    project.messageBus
        .connect()
        .subscribe(
            topic,
            object : CodyInlineEditActionNotifier {
              override fun afterAction(context: CodyInlineEditActionNotifier.Context) {
                future.complete(context)
              }
            })
    return future
  }

  // Run the IDE action specified by actionId.
  private fun triggerAction(actionId: String) {
    val action = ActionManager.getInstance().getAction(actionId)
    action.actionPerformed(
        AnActionEvent(
            null,
            mock(DataContext::class.java),
            "",
            action.templatePresentation.clone(),
            ActionManager.getInstance(),
            0))
  }

  private fun hasJavadocComment(text: String): Boolean {
    // TODO: Check for the exact contents once they are frozen.
    val javadocPattern = Pattern.compile("/\\*\\*.*?\\*/", Pattern.DOTALL)
    return javadocPattern.matcher(text).find()
  }

  companion object {

    // TODO: find the lowest value this can be for production, and use it
    // If it's too low the test may be flaky.
    // const val ASYNC_WAIT_TIMEOUT_SECONDS = 15000L

    const val ASYNC_WAIT_TIMEOUT_SECONDS = 15L
  }
}
