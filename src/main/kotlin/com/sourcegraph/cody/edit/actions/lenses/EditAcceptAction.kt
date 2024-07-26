package com.sourcegraph.cody.edit.actions.lenses

import com.sourcegraph.cody.agent.CodyAgentService

class EditAcceptAction :
    LensEditAction({ project, _, _, taskId ->
      CodyAgentService.withAgent(project) { it.server.acceptEditTask(taskId) }
    }) {
  companion object {
    const val ID = "cody.fixup.codelens.accept"
  }
}
