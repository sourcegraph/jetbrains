package com.sourcegraph.cody.chat.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.sourcegraph.cody.chat.ExportChatsBackgroundable
import com.sourcegraph.common.ui.DumbAwareBGTAction

class ExportChatsAction : DumbAwareBGTAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ExportChatsBackgroundable(project) { json ->
          println("LOL: ")
          println(json)
        }
        .queue()
  }
}
