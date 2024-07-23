package com.sourcegraph.cody.edit.actions

import com.intellij.openapi.diagnostic.Logger
import com.sourcegraph.cody.edit.EditCommandPrompt

class EditCodeAction :
    BaseEditCodeAction({ editor ->
      val project = editor.project
      if (project != null) {
        EditCommandPrompt(project, editor, "Edit Code with Cody")
        logger.warn("EditCodeAction invoked with null project")
      }
    }) {
  companion object {
    val logger = Logger.getInstance(EditCodeAction::class.java)
    const val ID: String = "cody.editCodeAction"
  }
}
