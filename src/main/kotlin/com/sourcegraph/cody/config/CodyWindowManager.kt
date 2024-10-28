package com.sourcegraph.cody.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol_generated.Window_DidChangeFocusParams
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

class CodyWindowAdapter(private val project: Project) : WindowAdapter() {
  private val authManager = CodyAuthenticationManager.getInstance()

  override fun windowActivated(e: WindowEvent?) {
    super.windowActivated(e)
    ApplicationManager.getApplication().executeOnPooledThread {
      authManager.getAuthenticationState()
    }
    CodyAgentService.withAgent(project) { agent: CodyAgent ->
      agent.server.window_didChangeFocus(Window_DidChangeFocusParams(true))
    }
  }

  override fun windowDeactivated(e: WindowEvent?) {
    super.windowDeactivated(e)
    CodyAgentService.withAgent(project) { agent: CodyAgent ->
      agent.server.window_didChangeFocus(Window_DidChangeFocusParams(false))
    }
  }
}
