package com.sourcegraph.cody.edit.actions.lenses

class EditUndoAction : LensEditAction({ agent, taskId -> agent.server.undoEditTask(taskId) }) {
  companion object {
    const val ID = "cody.fixup.codelens.undo"
  }
}
