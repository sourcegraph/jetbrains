package com.sourcegraph.cody.edit.actions.lenses

import com.sourcegraph.cody.agent.CodyAgentService

class EditUndoAction :
    LensEditAction({ project, _, taskId ->
      CodyAgentService.withAgent(project) { it.server.undoEditTask(taskId) }
    }) {
  companion object {
    const val ID = "cody.fixup.codelens.undo"
  }
}
