package com.sourcegraph.cody.edit

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.sourcegraph.cody.edit.widget.LensAction
import com.sourcegraph.cody.edit.widget.LensLabel
import com.sourcegraph.cody.edit.widget.LensSpinner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class DocumentCodeTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    configureFixture()
  }

  fun testGetsWorkingGroupLens() {
    val foldingRangeFuture = CompletableFuture<Unit>()
    project.messageBus
        .connect()
        .subscribe(
            CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES,
            object : CodyInlineEditActionNotifier {
              override fun afterAction(context: CodyInlineEditActionNotifier.Context) {
                testSelectionRange(context)
                foldingRangeFuture.complete(null)
              }
            })
    // TODO: Solve the missing document problem.
    //   Hypothesis: We never send a file-opened notification for configuration above
    //   - normally we do it in
    //   - workAroundUninitializedCodeBase might be a place to set a breakpoint at
    // Current problem is shown first in getFoldingRanges:
    // in agent.ts, registered authenticated request 'editTask/getFoldingRanges'
    // - File path passed in is "temp:///src/Foo.java"
    // - uri parses correctly
    // - this.workspace.getDocument(uri) is undefined
    //
    val editor = myFixture.editor
    assertFalse(editor.inlayModel.hasBlockElements())

    // Execute the action and await the working group lens.
    EditorTestUtil.executeAction(editor, "cody.documentCodeAction")

    val context = waitForTopic(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
    assertNotNull("Timed out waiting for working group lens", context)

    // The inlay should be up.
    assertTrue("Lens group inlay should be displayed", editor.inlayModel.hasBlockElements())

    // Lens group should match the expected structure.
    val lenses = context!!.session.lensGroup
    assertNotNull("Lens group should be displayed", lenses)

    val widgets = lenses!!.widgets
    assertEquals("Lens group should have 3 lenses", 3, widgets.size)
    assertTrue("First lens should be a spinner", widgets[0] is LensSpinner)
    assertTrue("Second lens should be a label", widgets[1] is LensLabel)
    assertTrue("Third lens should be an action", widgets[2] is LensAction)

    // We make the editTask/getFoldingRanges call before calling commands/document.
    // It's not supposed to be possible for them to be out of order, but this ensures
    // that if they wind up out of order, both paths are tested.
    try {
      foldingRangeFuture.get(5, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
      fail("Folding range future did not complete within 5 seconds")
    } catch (e: Exception) {
      fail("Unexpected exception: ${e.message}")
    }
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

  fun testTopLevelClass() {
    assert(true)
    // ...
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

  // Block until the passed topic gets a message, or until we time out.
  private fun waitForTopic(
      topic: Topic<CodyInlineEditActionNotifier>
  ): CodyInlineEditActionNotifier.Context? {
    val future =
        CompletableFuture<CodyInlineEditActionNotifier.Context?>()
            .completeOnTimeout(null, 5, TimeUnit.SECONDS)
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
}
