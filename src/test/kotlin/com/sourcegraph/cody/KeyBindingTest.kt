package com.sourcegraph.cody

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.swing.KeyStroke

@RunWith(JUnit4::class)
class KeyBindingTest : BasePlatformTestCase() {

  companion object {
    // These should match the table here:
    // https://linear.app/sourcegraph/document/keyboard-shortcuts-for-cody-on-jetbrains-2f8747a22530
    private val keysToActionIds =
      mapOf(
        // Most of these are bound in plugin.xml, some in EditCommandPrompt or misc.
        "cody.acceptAutocompleteAction" to arrayOf("TAB", "TAB"), // Windows, macOS
        "cody.triggerAutocomplete" to arrayOf("control alt P", "control alt P"),
        "cody.inlineEditAcceptAction" to arrayOf("ctrl shift EQUALS", "ctrl shift EQUALS"),

        // This one dispatches for cody.inlineEditUndoAction,
        // cody.inlineEditCancelAction and cody.inlineEditDismissAction:
        "cody.editCancelOrUndoAction" to arrayOf("ctrl alt BACK_SPACE", "ctrl alt BACK_SPACE"),
        "cody.documentCodeAction" to arrayOf("ctrl alt H", "ctrl alt H"),

        // This also handles cody.inlineEditRetryAction:
        "cody.editCodeAction" to arrayOf("ctrl alt COMMA", "ctrl alt ENTER"),

        // Need UI tests for testing Send Code Instructions hotkey, as there is no action id.
        // It's just a hardwired key listener in the EditCommandPrompt dialog.

        "cody.editShowDiffAction" to arrayOf("ctrl alt D", "ctrl alt K"),
        "cody.testCodeAction" to arrayOf("ctrl alt G", "ctrl alt G"),
        "cody.command.Explain" to arrayOf("ctrl alt 1", "ctrl alt 1"),
        "cody.command.Smell" to arrayOf("ctrl alt 2", "ctrl alt 2"),
        "cody.newChat" to arrayOf("ctrl alt 0", "ctrl alt 0"),
        "cody.openChat" to arrayOf("ctrl alt 9", "ctrl alt 9"),
        "sourcegraph.openFindPopup" to arrayOf("alt S", "alt S"),
      )
  }

  @Test
  fun testBindings() {
    for ((actionId, expectedKeys) in keysToActionIds) {
      val (windowsKey, macKey) = expectedKeys

      val action = ActionManager.getInstance().getAction(actionId)
      checkNotNull(action) { "Action not found: $actionId" }

      val shortcutSet = action.shortcutSet
      val actualKeyStrokes =
          shortcutSet.shortcuts
              .mapNotNull { shortcut ->
                when (shortcut) {
                  is KeyboardShortcut -> shortcut.firstKeyStroke
                  else -> null
                }
              }
              .toTypedArray()

      val platformSpecificExpectedKey = if (SystemInfoRt.isMac) macKey else windowsKey
      val expectedKeyStroke = KeyStroke.getKeyStroke(platformSpecificExpectedKey)

      assertTrue(
          "Incorrect keybinding for $actionId: expected $expectedKeyStroke, got ${actualKeyStrokes.contentToString()}",
          actualKeyStrokes.contains(expectedKeyStroke))
    }
  }
}
