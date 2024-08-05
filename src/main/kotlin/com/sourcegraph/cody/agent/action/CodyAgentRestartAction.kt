package com.sourcegraph.cody.agent.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.common.ui.DumbAwareEDTAction

class CodyAgentRestartAction : DumbAwareEDTAction() {
  override fun actionPerformed(event: AnActionEvent) {
    event.project?.let { CodyAgentService.getInstance(it).restartAgent(it) }
  }
}
