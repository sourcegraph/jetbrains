package com.sourcegraph.cody.chat.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import com.sourcegraph.cody.chat.AgentChatSession
import com.sourcegraph.cody.commands.CommandId
import com.sourcegraph.cody.context.ui.ActionInIgnoredFileNotification
import com.sourcegraph.cody.context.ui.RemoteRepoResolutionFailedNotification
import com.sourcegraph.cody.ignore.IgnoreOracle
import com.sourcegraph.cody.ignore.IgnorePolicy

abstract class BaseCommandAction : BaseChatAction() {

  abstract val myCommandId: CommandId

  override fun doAction(project: Project) {
    FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
      ApplicationManager.getApplication().assertIsDispatchThread()
      val file = FileDocumentManager.getInstance().getFile(editor.document)
      val protocolFile = file?.let { ProtocolTextDocument.fromVirtualFile(editor, it) } ?: return

      // If this file is ignored, display an error and stop.
      if (IgnoreOracle.getInstance(project).policyForUri(protocolFile.uri).get() != IgnorePolicy.USE) {
        ActionInIgnoredFileNotification().notify(project)
        return
      }

      CodyToolWindowContent.executeOnInstanceIfNotDisposed(project) {
        switchToChatSession(AgentChatSession.createFromCommand(project, myCommandId))
      }
    }
  }
}
