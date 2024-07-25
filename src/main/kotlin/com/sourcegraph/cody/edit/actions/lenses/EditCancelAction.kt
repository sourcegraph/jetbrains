package com.sourcegraph.cody.edit.actions.lenses

import com.sourcegraph.cody.agent.CodyAgentService

class EditCancelAction :
    LensEditAction({ project, _, taskId ->
      CodyAgentService.withAgent(project) { it.server.cancelEditTask(taskId) }
    }) {
  companion object {
    const val ID = "cody.fixup.codelens.cancel"
  }
}
