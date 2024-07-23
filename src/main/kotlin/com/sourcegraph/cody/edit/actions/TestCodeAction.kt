package com.sourcegraph.cody.edit.actions

import com.sourcegraph.cody.agent.CodyAgentService

class TestCodeAction :
    BaseEditCodeAction({ editor ->
      editor.project?.let { project ->
        CodyAgentService.withAgent(project) { agent -> agent.server.commandsTest() }
      }
    }) {
  companion object {
    const val ID: String = "cody.testCodeAction"
  }
}
