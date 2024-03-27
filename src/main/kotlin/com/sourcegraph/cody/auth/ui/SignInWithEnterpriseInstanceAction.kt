package com.sourcegraph.cody.auth.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.sourcegraph.cody.CodyToolWindowFactory
import com.sourcegraph.cody.config.CodyPersistentAccountsHost
import com.sourcegraph.cody.config.signInWithSourcegrapDialog
import com.sourcegraph.config.ConfigUtil

class SignInWithEnterpriseInstanceAction(
    private val defaultServer: String = ConfigUtil.DOTCOM_URL
) : DumbAwareAction("Sign in with Sourcegraph") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val accountsHost = CodyPersistentAccountsHost(project)
    val dialog =
        signInWithSourcegrapDialog(
            project,
            e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT),
            accountsHost::isAccountUnique)

    dialog.setServer(defaultServer)
    if (dialog.showAndGet()) {
      accountsHost.addAccount(
          dialog.server, dialog.login, dialog.displayName, dialog.token, dialog.id)
      if (ConfigUtil.isCodyEnabled()) {
        // Open Cody sidebar
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow(CodyToolWindowFactory.TOOL_WINDOW_ID)
        toolWindow?.setAvailable(true, null)
        toolWindow?.activate {}
      }
    }
  }
}
