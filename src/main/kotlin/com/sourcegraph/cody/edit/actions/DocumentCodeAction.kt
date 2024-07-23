package com.sourcegraph.cody.edit.actions

import com.sourcegraph.cody.agent.CodyAgentService

class DocumentCodeAction :
    BaseEditCodeAction({ editor ->
      editor.project?.let { project ->
        CodyAgentService.withAgent(project) { agent -> agent.server.commandsDocument() }
      }
    }) {
  companion object {
    const val ID: String = "cody.documentCodeAction"
  }
}
