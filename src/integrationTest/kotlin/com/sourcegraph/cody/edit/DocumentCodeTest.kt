package com.sourcegraph.cody.edit

import com.intellij.testFramework.runInEdtAndGet
import com.jetbrains.rd.util.AtomicInteger
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_DISPLAY_ACTION_GROUPS
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_DISPLAY_WORKING_GROUP
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_FOLDING_RANGES
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_PERFORM_ACCEPT
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_PERFORM_ACCEPT_ALL
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_PERFORM_REJECT
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_PERFORM_UNDO
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_TASK_FINISHED
import com.sourcegraph.cody.edit.actions.DocumentCodeAction
import com.sourcegraph.cody.edit.sessions.FixupSession
import com.sourcegraph.cody.edit.widget.LensAction
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensHotkey
import com.sourcegraph.cody.edit.widget.LensIcon
import com.sourcegraph.cody.edit.widget.LensLabel
import com.sourcegraph.cody.edit.widget.LensSpinner
import com.sourcegraph.cody.util.CodyIntegrationTextFixture
import com.sourcegraph.cody.util.CustomJunitClassRunner
import junit.framework.TestCase
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(CustomJunitClassRunner::class)
class DocumentCodeTest : CodyIntegrationTextFixture() {

  @Test
  fun testGetsFoldingRanges() {
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_FOLDING_RANGES)

    val selection = activeSession().selectionRange
    assertNotNull("Selection should have been set", selection)
    // We set the selection range to whatever the protocol returns.
    // If a 0-width selection turns out to be reasonable we can adjust or remove this test.
    assertFalse(
        "Selection range should not be zero-width", selection!!.startOffset == selection.endOffset)
    // A more robust check is to see if the selection "range" is just the caret position.
    // If so, then our fallback range somehow made the round trip, which is bad. The lenses will
    // go in the wrong places, etc.
    val caret = runInEdtAndGet { myFixture.editor.caretModel.primaryCaret.offset }
    assertFalse(
        "Selection range should not equal the caret position",
        selection.startOffset == caret && selection.endOffset == caret)
  }

  @Ignore
  @Test
  fun testGetsWorkingGroupLens() {
    val assertsExecuted = AtomicInteger(0)
    val showWorkingGroupSessionStateListener =
        object : FixupService.ActiveFixupSessionStateListener {
          // The listener is notified by ::showDiffLensGroup and the param is true only when we are
          // showing the working group. This is the best place to catch and verify the working lens
          // group as it can change to the accept lens group outside of this listener.
          override fun fixupSessionStateChanged(isInProgress: Boolean) {
            assertInlayIsShown()

            if (isInProgress) {
              val lenses = activeSession().getLensGroupManager().getLensGroups()
              // Lens group should match the expected structure.
              assertNotNull("Lens group should be displayed", lenses)
              val theWidgets = lenses!!.widgets

              assertEquals("Lens group should have 8 widgets", 8, theWidgets.size)
              assertTrue("Zeroth lens group should be an icon", theWidgets[0] is LensIcon)
              assertTrue(
                  "First lens group is space separator label",
                  (theWidgets[1] as LensLabel).text == " ")
              assertTrue("Second lens group is a spinner", theWidgets[2] is LensSpinner)
              assertTrue(
                  "Third lens group is space separator label",
                  (theWidgets[3] as LensLabel).text == " ")
              assertTrue(
                  "Fourth lens group is a description label",
                  (theWidgets[4] as LensLabel).text == "Generating Code Edits")
              assertTrue(
                  "Fifth lens group is separator label",
                  (theWidgets[5] as LensLabel).text == LensGroupFactory.SEPARATOR)
              assertTrue("Sixth lens group should be an action", theWidgets[6] is LensAction)
              assertTrue(
                  "Seventh lens group should be a label with a hotkey", theWidgets[7] is LensHotkey)
              assertsExecuted.incrementAndGet()
            }
          }
        }
    try {
      FixupService.getInstance(project).addListener(showWorkingGroupSessionStateListener)
      runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_WORKING_GROUP)
      TestCase.assertEquals(
          "Asserts not executed or executed more than once", 1, assertsExecuted.get())
    } finally {
      FixupService.getInstance(project).removeListener(showWorkingGroupSessionStateListener)
    }
  }

  @Test
  fun testShowsAcceptLens() {
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_DIFF_GROUP)
    assertInlayIsShown()

    // Lens group should match the expected structure.
    val lenses = activeSession().diffLensGroup
    assertNotNull("Lens group should be displayed", lenses)

    val widgets = lenses!!.widgets
    // There are 13 widgets as of the time of writing, but the UX could change, so check robustly.
    assertTrue("Lens group should have at least 4 widgets", widgets.size >= 4)
    assertNotNull(
        "Lens group should contain Accept all action",
        widgets.find { widget ->
          widget is LensAction && widget.actionId == FixupSession.ACTION_ACCEPT_ALL
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
    assertNotNull(
      "Lens group should contain accept action",
      widgets.find { widget ->
        widget is LensAction && widget.actionId == FixupSession.ACTION_ACCEPT
      })
    assertNotNull(
      "Lens group should contain reject action",
      widgets.find { widget ->
        widget is LensAction && widget.actionId == FixupSession.ACTION_REJECT
      })

    // Make sure a doc comment was inserted.
    assertTrue(hasJavadocComment(myFixture.editor.document.text))
  }

  @Test
  fun testAcceptAll() {
    assertNoActiveSession()
    assertNoInlayShown()

    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_DIFF_GROUP)

    assertInlayIsShown()
    assertActiveSession()

    runAndWaitForNotifications(
        FixupSession.ACTION_ACCEPT, TOPIC_PERFORM_ACCEPT_ALL, TOPIC_TASK_FINISHED)

    assertNoInlayShown()
    assertNoActiveSession()
  }

  @Test
  fun testAccept() {
    assertNoActiveSession()
    assertNoInlayShown()

    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_DIFF_GROUP)

    assertInlayIsShown()
    assertActiveSession()

    runAndWaitForNotifications(
      FixupSession.ACTION_ACCEPT, TOPIC_PERFORM_ACCEPT, TOPIC_TASK_FINISHED)

    assertNoInlayShown()
    assertNoActiveSession()
  }

  @Test
  fun testReject() {
    val originalDocument = myFixture.editor.document.text
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_DIFF_GROUP)
    assertNotSame(
      "Expected document to be changed", originalDocument, myFixture.editor.document.text)
    assertInlayIsShown()

    //TODO: JM, not sure if this is ACTION_UNDO OR ACTION_ACCEPT
    runAndWaitForNotifications(FixupSession.ACTION_ACCEPT, TOPIC_PERFORM_REJECT, TOPIC_TASK_FINISHED)
    assertEquals(
      "Expected document changes to be reverted",
      originalDocument,
      myFixture.editor.document.text)
    assertNoInlayShown()
  }

  @Test
  fun testUndo() {
    val originalDocument = myFixture.editor.document.text
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_DIFF_GROUP)
    assertNotSame(
        "Expected document to be changed", originalDocument, myFixture.editor.document.text)
    assertInlayIsShown()

    runAndWaitForNotifications(FixupSession.ACTION_UNDO, TOPIC_PERFORM_UNDO, TOPIC_TASK_FINISHED)
    assertEquals(
        "Expected document changes to be reverted",
        originalDocument,
        myFixture.editor.document.text)
    assertNoInlayShown()
  }
}
