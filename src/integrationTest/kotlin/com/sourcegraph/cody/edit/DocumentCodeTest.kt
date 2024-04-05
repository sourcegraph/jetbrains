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
    val foldingRangeFuture = listenForFoldingRangeReply()

    val editor = myFixture.editor
    assertFalse(editor.inlayModel.hasBlockElements())

    // Execute the action and await the working group lens.
    runInEdtAndWait { EditorTestUtil.executeAction(editor, "cody.documentCodeAction") }

    val context = waitForTopic(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
    assertNotNull("Timed out waiting for working group lens", context)

    // The inlay should be up.
    assertTrue("Lens group inlay should be displayed", editor.inlayModel.hasBlockElements())

    // This is done now.
    runInEdtAndWait { testSelectionRange(foldingRangeFuture.get()) }

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

  private fun listenForFoldingRangeReply():
      CompletableFuture<CodyInlineEditActionNotifier.Context> {
    val foldingRangeFuture = CompletableFuture<CodyInlineEditActionNotifier.Context>()
    project.messageBus
        .connect()
        .subscribe(
            CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES,
            object : CodyInlineEditActionNotifier {
              override fun afterAction(context: CodyInlineEditActionNotifier.Context) {
                foldingRangeFuture.complete(context)
              }
            })
    return foldingRangeFuture
  }

  @RequiresEdt
  private fun testSelectionRange(context: CodyInlineEditActionNotifier.Context) {
    // TODO: Test/check selection range & fail test
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

  // Next up:
  //  - test Cancel
  //  - test Accept
  //  - test workspace/edit
  //    - assertTrue(myFixture.editor.document.text.contains("/\\*"))
  //  - test Undo

  //  fun testTopLevelClass() {
  //    assert(true)
  //    // ...
  //  }

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

  // Block until the passed topic gets a message, or until we time out.
  private fun waitForTopic(
      topic: Topic<CodyInlineEditActionNotifier>
  ): CodyInlineEditActionNotifier.Context? {
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
    return future.get()
  }

  companion object {

    // TODO: find the lowest value this can be for production, and use it
    // If it's too low the test may be flaky.

    const val ASYNC_WAIT_TIMEOUT_SECONDS = 50L // 5L for non-debugging
  }
}
