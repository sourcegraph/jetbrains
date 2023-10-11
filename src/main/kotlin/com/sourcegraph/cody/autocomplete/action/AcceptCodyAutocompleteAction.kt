package com.sourcegraph.cody.autocomplete.action

import com.intellij.openapi.editor.actionSystem.EditorAction

/**
 * The action that gets triggered when the user accepts a Cody completion.
 *
 * The action works by reading the Inlay at the caret position and inserting the completion text
 * into the editor.
 */
object AcceptCodyAutocompleteAction : EditorAction(AcceptAutocompleteActionHandler()), CodyAction
