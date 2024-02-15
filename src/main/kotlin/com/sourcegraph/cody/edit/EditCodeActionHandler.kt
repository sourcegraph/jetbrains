package com.sourcegraph.cody.edit

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.DumbAware
import com.sourcegraph.cody.autocomplete.action.CodyAction

class EditCodeAction : EditorAction(EditCodeActionHandler()), CodyAction, DumbAware

class EditCodeActionHandler : EditorActionHandler() {
  private val logger = Logger.getInstance(EditCodeActionHandler::class.java)

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return !editor.isDisposed
  }

  override fun doExecute(editor: Editor, where: Caret?, dataContext: DataContext?) {
    InlineFixups.instance.startCodeEdit(editor)
  }
}
