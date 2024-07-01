package com.sourcegraph.cody.auth

import com.intellij.openapi.project.Project
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.config.getFirstAccountOrNull
import com.sourcegraph.cody.initialization.Activity

class SelectOneOfTheAccountsAsActive : Activity {

  override fun runActivity(project: Project) {
    val instance = CodyAuthenticationManager.getInstance(project)
    val initialAccount = instance.account ?: instance.getAccounts().getFirstAccountOrNull()
    if (initialAccount == null) {
      // The call to refreshPanelsVisibility() is needed to update the UI when there is no account.
      CodyToolWindowContent.executeOnInstanceIfNotDisposed(project) { refreshPanelsVisibility() }
    } else {
      instance.setActiveAccount(initialAccount)
    }
  }
}
