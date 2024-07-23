package com.sourcegraph.cody.edit.actions.lenses

import com.intellij.openapi.application.runInEdt

class EditRetryAction :
    LensEditAction({ agent, taskId ->
      runInEdt {
        //    // Close loophole where you can keep retrying after the ignore policy changes.
        //    if (controller.isEligibleForInlineEdit(editor)) {
        //      EditCommandPrompt(controller, editor, "Edit instructions and Retry", instruction)
        //    }
      }
    }) {
  companion object {
    const val ID = "cody.fixup.codelens.retry"
  }
}
