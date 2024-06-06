package com.sourcegraph.cody.edit

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndWait
import com.sourcegraph.cody.edit.sessions.FixupSession
import com.sourcegraph.cody.edit.widget.LensAction
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensHotkey
import com.sourcegraph.cody.edit.widget.LensIcon
import com.sourcegraph.cody.edit.widget.LensLabel
import com.sourcegraph.cody.edit.widget.LensSpinner
import com.sourcegraph.cody.util.CodyIntegrationTextFixture
import java.util.concurrent.TimeoutException
import org.junit.jupiter.api.assertDoesNotThrow

class DocumentCodeTest : CodyIntegrationTextFixture() {

  fun testGetsFoldingRanges() {
    val foldingRangeFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES)
    executeDocumentCodeAction()
    runInEdtAndWait {
      val rangeContext =
          assertDoesNotThrow("Exception while waiting for folding ranges") {
            try {
              foldingRangeFuture.get()
            } catch (t: TimeoutException) {
              fail("Timed out waiting for folding ranges")
              null
            }
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
    assertEquals("Lens group should have 6 widgets", 8, widgets.size)
    assertTrue("Zeroth lens group should be an icon", widgets[0] is LensIcon)
    assertTrue("First lens group is space separator label", (widgets[1] as LensLabel).text == " ")
    assertTrue("Second lens group is a spinner", widgets[2] is LensSpinner)
    assertTrue("Third lens group is space separator label", (widgets[3] as LensLabel).text == " ")
    assertTrue(
        "Fourth lens group is a description label",
        (widgets[4] as LensLabel).text == "Generating Code Edits")
    assertTrue(
        "Fifth lens group is separator label",
        (widgets[5] as LensLabel).text == LensGroupFactory.SEPARATOR)
    assertTrue("Sixth lens group should be an action", widgets[6] is LensAction)
    assertTrue("Seventh lens group should be a label with a hotkey", widgets[7] is LensHotkey)
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

    val performAcceptFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_PERFORM_ACCEPT)
    val taskFnishedFuture = subscribeToTopic(CodyInlineEditActionNotifier.TOPIC_TASK_FINISHED)

    triggerAction(FixupSession.ACTION_ACCEPT)

    assertNotNull("Timed out waiting for Accept action to complete", performAcceptFuture.get())
    assertNotNull("Timed out waiting for CodyTaskState.Finished state", taskFnishedFuture.get())

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
}
