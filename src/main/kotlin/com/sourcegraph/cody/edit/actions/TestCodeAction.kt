package com.sourcegraph.cody.edit.actions

import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.edit.FixupService

class TestCodeAction :
    BaseEditCodeAction({ editor ->
      editor.project?.let { project ->
        CodyAgentService.withAgent(project) { agent ->
          val result = agent.server.commandsTest().get()
          FixupService.getInstance(project).completedFixups[result.id] = result
        }
      }
    }) {
  companion object {
    const val ID: String = "cody.testCodeAction"
  }
}
