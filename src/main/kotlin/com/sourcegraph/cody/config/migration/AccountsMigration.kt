package com.sourcegraph.cody.config.migration

import com.intellij.openapi.project.Project
import com.sourcegraph.cody.auth.CodyAccount
import com.sourcegraph.cody.auth.CodyAccountService
import com.sourcegraph.cody.auth.deprecated.DeprecatedCodyAccountManager

object AccountsMigration {
  fun migrate(project: Project) {
    val codyAccountManager = DeprecatedCodyAccountManager.getInstance()
    codyAccountManager.getAccounts().forEach { oldAccount ->
      val token = codyAccountManager.getTokenForAccount(oldAccount)
      if (token != null) {
        val account = CodyAccount(oldAccount.server, token)
        CodyAccountService.getInstance(project).setActiveAccount(account)
      }
    }

    codyAccountManager.account?.server?.let {
      val account = CodyAccountService.getInstance(project).loadAccount(it.url)
      CodyAccountService.getInstance(project).setActiveAccount(account)
    }
  }
}
