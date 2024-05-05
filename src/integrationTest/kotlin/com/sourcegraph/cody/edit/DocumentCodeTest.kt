package com.sourcegraph.cody.edit

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
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
import org.mockito.Mockito.mock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DocumentCodeTest : BasePlatformTestCase() {
  private val logger = Logger.getInstance(DocumentCodeTest::class.java)

  override fun setUp() {
    super.setUp()
    configureFixture()
  }

  override fun tearDown() {
    FixupService.getInstance(myFixture.project).getActiveSession()?.apply {
      try {
        finish()
      } catch (x: Exception) {
        logger.warn("Error shutting down session", x)
      }
    }
    // TODO: Notify the Agent that all documents were closed.
    super.tearDown()
  }

  fun testGetsFoldingRanges() {
    val foldingRangeFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES)
    executeDocumentCodeAction()
    runInEdtAndWait {
      val rangeContext = foldingRangeFuture.get()
      assertNotNull(rangeContext)
      // Ensure we were able to get the selection range.
      val selection = rangeContext!!.session.selectionRange
      assertNotNull("Selection should have been set", selection)
      // We set the selection range to whatever the protocol returns.
      // If a 0-width selection turns out to be reasonable we can adjust or remove this test.
      assertFalse("Selection range should not be zero-width", selection!!.start == selection.end)
      // A more robust check is to see if the selection "range" is just the caret position.
      // If so, then our fallback range somehow made the round trip, which is bad. The lenses will
      // go
      // in the wrong places, etc.
      val document = myFixture.editor.document
      val startOffset = selection.start.toOffset(document)
      val endOffset = selection.end.toOffset(document)
      val caret = myFixture.editor.caretModel.primaryCaret.offset
      assertFalse(
          "Selection range should not equal the caret position",
          startOffset == caret && endOffset == caret)
    }
  }

  fun testGetsWorkingGroupLens() {
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

  fun testShowsAcceptLens() {
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

  fun testAccept() {
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

  fun testUndo() {
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
    // spotless:off
    myFixture.configureByText(
        "Foo.java",
        """
import java.util.*;

public class Foo {

    public void foo() {
        List<Integer> mystery = new ArrayList<>();
        mystery.add(0);
        mystery.add(1);
        for (int i = 2; i < 10; i++) {
          mystery.add(mystery.get(i - 1) + mystery.get(i - 2));
        }
        
        <caret>for (int i = 0; i < 10; i++) {
          System.out.println(mystery.get(i));
        }
    }
}
""") // spotless:on
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
    // const val ASYNC_WAIT_TIMEOUT_SECONDS = 10000L //debug
    const val ASYNC_WAIT_TIMEOUT_SECONDS = 15L
  }
}
