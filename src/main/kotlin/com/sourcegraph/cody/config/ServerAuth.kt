package com.sourcegraph.cody.config

import com.intellij.openapi.project.Project
import com.sourcegraph.config.ConfigUtil.DOTCOM_URL

data class ServerAuth(
    val instanceUrl: String,
    val accessToken: String = "",
    val customRequestHeaders: String = ""
)

object ServerAuthLoader {

  @JvmStatic
  fun loadServerAuth(project: Project): ServerAuth {
    val authManager = CodyAuthenticationManager.instance
    val defaultAccount = authManager.getActiveAccount(project)
    if (defaultAccount != null) {
      val token = authManager.getTokenForAccount(defaultAccount) ?: ""
      return ServerAuth(
          defaultAccount.server.url, token, defaultAccount.server.customRequestHeaders)
    }
    return ServerAuth(DOTCOM_URL)
  }
}
