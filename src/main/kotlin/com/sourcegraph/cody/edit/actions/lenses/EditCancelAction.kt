package com.sourcegraph.cody.edit.actions.lenses

class EditCancelAction : LensEditAction({ agent, taskId -> agent.server.cancelEditTask(taskId) }) {
  companion object {
    const val ID = "cody.fixup.codelens.cancel"
  }
}
