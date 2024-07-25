package com.sourcegraph.cody.edit.actions.lenses

import com.intellij.openapi.application.runInEdt
import com.sourcegraph.cody.edit.EditCommandPrompt
import com.sourcegraph.cody.edit.FixupService

class EditRetryAction :
    LensEditAction({ project, editor, taskId ->
      runInEdt {
        val completedFixup = FixupService.getInstance(project).completedFixups[taskId.id]
        if (completedFixup != null) {
          runInEdt {
            EditCommandPrompt(project, editor, "Edit instructions and Retry", completedFixup)
          }
        }
      }
    }) {
  companion object {
    const val ID = "cody.fixup.codelens.retry"
  }
}
