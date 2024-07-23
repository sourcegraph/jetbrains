package com.sourcegraph.cody.edit.actions.lenses

class EditAcceptAction : LensEditAction({ agent, taskId -> agent.server.acceptEditTask(taskId) }) {
  companion object {
    const val ID = "cody.fixup.codelens.accept"
  }
}
