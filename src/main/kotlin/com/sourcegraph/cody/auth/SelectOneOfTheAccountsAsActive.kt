package com.sourcegraph.cody.auth

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.config.CodyAccount
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.config.getFirstAccountOrNull
import com.sourcegraph.cody.initialization.Activity

class SelectOneOfTheAccountsAsActive : Activity {

  private val logger = Logger.getInstance(SelectOneOfTheAccountsAsActive::class.java)

  override fun runActivity(project: Project) {
    val manager = CodyAuthenticationManager.instance
    activateTestAccountIfAvailable(project, manager)
    activateAnyAccount(project, manager)
  }

  private fun activateTestAccountIfAvailable(project: Project, manager: CodyAuthenticationManager) {
    val testToken = System.getenv("SRC_ACCESS_TOKEN")
    if (testToken != null) {
      val testAccount = CodyAccount("test", "Test User")
      manager.updateAccountToken(testAccount, testToken)
      manager.setActiveAccount(project, testAccount)
      logger.warn("Test account with external SRC_ACCESS_TOKEN selected as active")
    }
  }

  private fun activateAnyAccount(project: Project, manager: CodyAuthenticationManager) {
    if (manager.getActiveAccount(project) == null) {
      val newActiveAccount = manager.getAccounts().getFirstAccountOrNull()
      manager.setActiveAccount(project, newActiveAccount)
      val codyToolWindowContent = CodyToolWindowContent.getInstance(project)
      codyToolWindowContent.refreshPanelsVisibility()
    }
  }
}
