package com.sourcegraph.cody.chat.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import com.sourcegraph.cody.commands.CommandId
import com.sourcegraph.cody.ignore.ActionInIgnoredFileNotification
import com.sourcegraph.cody.ignore.IgnoreOracle
import com.sourcegraph.cody.ignore.IgnorePolicy
import com.sourcegraph.common.ui.DumbAwareEDTAction
import java.util.concurrent.Callable

abstract class BaseCommandAction : DumbAwareEDTAction() {

  abstract val myCommandId: CommandId

  override fun actionPerformed(event: AnActionEvent) {
    doAction(event.project ?: return)
  }

  open fun doAction(project: Project) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    FileEditorManager.getInstance(project).selectedTextEditor?.let { editor ->
      val file = FileDocumentManager.getInstance().getFile(editor.document)
      val protocolFile =
          file?.let { ProtocolTextDocument.fromVirtualEditorFile(editor, it) } ?: return

      ReadAction.nonBlocking(
              Callable { IgnoreOracle.getInstance(project).policyForUri(protocolFile.uri).get() })
          .expireWith(project)
          .finishOnUiThread(ModalityState.NON_MODAL) {
            when (it) {
              IgnorePolicy.USE -> {
                CodyToolWindowContent.executeOnInstanceIfNotDisposed(project) {
                  // Race: The selected text editor could change before IgnoreOracle completes, and
                  // the command runs on the wrong document. Ignore rules will still be enforced by
                  // prompt construction so this is a correctness issue but not a safety issue.
                  // TODO: Fix this race by giving commands an explicit document to act on.
                  TODO("NYI, run TypeScript commands instead.")
                }
              }
              else -> {
                // This file is ignored. Display an error and stop.
                ActionInIgnoredFileNotification.maybeNotify(project)
              }
            }
          }
          .submit(AppExecutorUtil.getAppExecutorService())
    }
  }
}
