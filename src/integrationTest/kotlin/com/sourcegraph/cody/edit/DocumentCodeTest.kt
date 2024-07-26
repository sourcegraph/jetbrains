package com.sourcegraph.cody.edit

import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_DISPLAY_ACCEPT_GROUP
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_DISPLAY_WORKING_GROUP
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_PERFORM_ACCEPT
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_PERFORM_UNDO
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier.Companion.TOPIC_TASK_FINISHED
import com.sourcegraph.cody.edit.actions.DocumentCodeAction
import com.sourcegraph.cody.edit.actions.lenses.EditAcceptAction
import com.sourcegraph.cody.edit.actions.lenses.EditRetryAction
import com.sourcegraph.cody.edit.actions.lenses.EditUndoAction
import com.sourcegraph.cody.edit.widget.LensAction
import com.sourcegraph.cody.edit.widget.LensHotkey
import com.sourcegraph.cody.edit.widget.LensIcon
import com.sourcegraph.cody.edit.widget.LensLabel
import com.sourcegraph.cody.edit.widget.LensSpinner
import com.sourcegraph.cody.edit.widget.LensWidgetGroup
import com.sourcegraph.cody.util.CodyIntegrationTextFixture
import com.sourcegraph.cody.util.CustomJunitClassRunner
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(CustomJunitClassRunner::class)
class DocumentCodeTest : CodyIntegrationTextFixture() {
  @Ignore
  @Test
  fun testGetsWorkingGroupLens() {
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_WORKING_GROUP)

    val inlayModel = myFixture.editor.inlayModel
    val blockElements = inlayModel.getBlockElementsInRange(0, myFixture.editor.document.textLength)
    val lensesGroups = blockElements.mapNotNull { it.renderer as? LensWidgetGroup }

    assertEquals("There should be exactly one lenses group", 1, lensesGroups.size)

    // Lens group should match the expected structure.
    val theWidgets = lensesGroups.first().widgets

    assertEquals("Lens group should have 8 widgets", 8, theWidgets.size)
    assertTrue("Zeroth lens group should be an icon", theWidgets[0] is LensIcon)
    assertTrue(
        "First lens group is space separator label", (theWidgets[1] as LensLabel).text == " ")
    assertTrue("Second lens group is a spinner", theWidgets[2] is LensSpinner)
    assertTrue(
        "Third lens group is space separator label", (theWidgets[3] as LensLabel).text == " ")
    assertTrue(
        "Fourth lens group is a description label",
        (theWidgets[4] as LensLabel).text == "Generating Code Edits")
    assertTrue(
        "Fifth lens group is separator label",
        (theWidgets[5] as LensLabel).text == LensesService.SEPARATOR)
    assertTrue("Sixth lens group should be an action", theWidgets[6] is LensAction)
    assertTrue("Seventh lens group should be a label with a hotkey", theWidgets[7] is LensHotkey)
  }

  @Test
  fun testShowsAcceptLens() {
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_ACCEPT_GROUP)
    assertInlayIsShown()

    // Lens group should match the expected structure.
    val inlayModel = myFixture.editor.inlayModel
    val blockElements = inlayModel.getBlockElementsInRange(0, myFixture.editor.document.textLength)
    val lensesGroups = blockElements.mapNotNull { it.renderer as? LensWidgetGroup }
    val lenses = lensesGroups.firstOrNull()

    assertNotNull("Lens group should be displayed", lenses)

    val widgets = lenses!!.widgets
    // There are 13 widgets as of the time of writing, but the UX could change, so check robustly.
    assertTrue("Lens group should have at least 4 widgets", widgets.size >= 4)
    assertNotNull(
        "Lens group should contain Accept action",
        widgets.find { widget -> widget is LensAction && widget.actionId == EditAcceptAction.ID })
    assertNotNull(
        "Lens group should contain Show Diff action",
        widgets.find { widget ->
          widget is LensAction && widget.actionId == "cody.fixup.codelens.diff"
        })
    assertNotNull(
        "Lens group should contain Show Undo action",
        widgets.find { widget -> widget is LensAction && widget.actionId == EditUndoAction.ID })
    assertNotNull(
        "Lens group should contain Show Retry action",
        widgets.find { widget -> widget is LensAction && widget.actionId == EditRetryAction.ID })

    // Make sure a doc comment was inserted.
    assertTrue(hasJavadocComment(myFixture.editor.document.text))
  }

  @Test
  fun testAccept() {
    assertNoInlayShown()
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_ACCEPT_GROUP)
    assertInlayIsShown()
    runAndWaitForNotifications(EditAcceptAction.ID, TOPIC_PERFORM_ACCEPT, TOPIC_TASK_FINISHED)
    assertNoInlayShown()
  }

  @Test
  fun testUndo() {
    val originalDocument = myFixture.editor.document.text
    runAndWaitForNotifications(DocumentCodeAction.ID, TOPIC_DISPLAY_ACCEPT_GROUP)
    assertNotSame(
        "Expected document to be changed", originalDocument, myFixture.editor.document.text)
    assertInlayIsShown()

    runAndWaitForNotifications(EditUndoAction.ID, TOPIC_PERFORM_UNDO, TOPIC_TASK_FINISHED)
    assertEquals(
        "Expected document changes to be reverted",
        originalDocument,
        myFixture.editor.document.text)
    assertNoInlayShown()
  }
}
