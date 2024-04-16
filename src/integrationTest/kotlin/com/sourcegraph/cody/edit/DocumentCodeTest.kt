package com.sourcegraph.cody.edit

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.sourcegraph.cody.edit.widget.LensAction
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensLabel
import com.sourcegraph.cody.edit.widget.LensSpinner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class DocumentCodeTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    configureFixture()
  }

  override fun tearDown() {
    // TODO: Notify the Agent that all documents were closed.
    super.tearDown()
  }

  fun testGetsWorkingGroupLens() {
    // Do this before starting the edit operation, in order to be subscribed before
    // the synchronous notification is sent, because it is not buffered or queued anywhere.
    val foldingRangeFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES)

    val editor = myFixture.editor
    assertFalse(editor.inlayModel.hasBlockElements())

    // Execute the action and await the working group lens.
    runInEdtAndWait { EditorTestUtil.executeAction(editor, "cody.documentCodeAction") }

    val context = waitForTopic(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
    assertNotNull("Timed out waiting for working group lens", context)

    // The inlay should be up.
    assertTrue("Lens group inlay should be displayed", editor.inlayModel.hasBlockElements())

    // We've finished receiving the folding range by now.
    runInEdtAndWait {
      val rangeContext = foldingRangeFuture.get()
      assertNotNull(rangeContext)
      testSelectionRange(rangeContext!!)
    }

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

    // This avoids an error saying the spinner hasn't shut down, at the end of the test.
    runInEdtAndWait { Disposer.dispose(lenses) }
  }

  fun testShowsAcceptLens() {
    val editor = myFixture.editor
    assertFalse(editor.inlayModel.hasBlockElements())

    runInEdtAndWait { EditorTestUtil.executeAction(editor, "cody.documentCodeAction") }

    val context = waitForTopic(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ACCEPT_GROUP)
    assertNotNull("Timed out waiting for Accept group lens", context)

    // The inlay should be up.
    assertTrue("Lens group inlay should be displayed", editor.inlayModel.hasBlockElements())

    // Lens group should match the expected structure.
    val lenses = context!!.session.lensGroup
    assertNotNull("Lens group should be displayed", lenses)

    val widgets = lenses!!.widgets
    // There are 13 widgets as of the time of writing, but the UX could change, so check robustly.
    assertTrue("Lens group should have at least 4 widgets", widgets.size >= 4)
    assertNotNull("Lens group should contain Accept action",
            widgets.find { widget -> widget is LensAction && widget.command == FixupSession.COMMAND_ACCEPT })
    assertNotNull("Lens group should contain Show Diff action",
            widgets.find { widget -> widget is LensAction && widget.command == FixupSession.COMMAND_DIFF })
    assertNotNull("Lens group should contain Show Undo action",
            widgets.find { widget -> widget is LensAction && widget.command == FixupSession.COMMAND_UNDO })
    assertNotNull("Lens group should contain Show Retry action",
            widgets.find { widget -> widget is LensAction && widget.command == FixupSession.COMMAND_RETRY })

    // Make sure a doc comment was inserted.
    assertTrue(hasJavadocComment(editor.document.text))

    // This avoids an error saying the spinner hasn't shut down, at the end of the test.
    runInEdtAndWait { Disposer.dispose(lenses) }
  }

  fun testUndo() {
    // Kick off an 'editCommands/document' request.
    val editor = myFixture.editor
    runInEdtAndWait { EditorTestUtil.executeAction(editor, "cody.documentCodeAction") }

    val undoFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_PERFORM_UNDO)

    // The Accept/Retry/Undo group is now showing.
    // TODO: Send the Undo action as if the user triggered it.
    //   - we will need to turn our pseudo-actions into real IDE actions

    val context = undoFuture.get()
    assertNotNull("Timed out waiting for Undo action", context)
  }

  @RequiresEdt
  private fun testSelectionRange(context: CodyInlineEditActionNotifier.Context) {
    // Ensure we were able to get the selection range.
    val selection = context.session.selectionRange
    assertNotNull("Selection should have been set", selection)
    // We set the selection range to whatever the protocol returns.
    // If a 0-width selection turns out to be reasonable we can adjust or remove this test.
    assertFalse("Selection range should not be zero-width", selection!!.start == selection.end)
    // A more robust check is to see if the selection "range" is just the caret position.
    // If so, then our fallback range somehow made the round trip, which is bad. The lenses will go
    // in the wrong places, etc.
    val document = myFixture.editor.document
    val startOffset = selection.start.toOffset(document)
    val endOffset = selection.end.toOffset(document)
    val caret = myFixture.editor.caretModel.primaryCaret.offset
    assertFalse(
        "Selection range should not equal the caret position",
        startOffset == caret && endOffset == caret)
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

  // Block until the passed topic gets a message, or until we time out.
  private fun waitForTopic(
      topic: Topic<CodyInlineEditActionNotifier>
  ): CodyInlineEditActionNotifier.Context? {
    return subscribeToTopic(topic).get()
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
