package com.sourcegraph.cody.config

import com.intellij.openapi.project.Project
import com.sourcegraph.config.ConfigUtil

data class ServerAuth(
    val instanceUrl: String,
    val accessToken: String,
    val customRequestHeaders: String
)

object ServerAuthLoader {

  @JvmStatic
  fun loadServerAuth(project: Project): ServerAuth {
    val codyAuthenticationManager = CodyAuthenticationManager.instance
    val defaultAccount = codyAuthenticationManager.getActiveAccount(project)
    if (defaultAccount != null) {
      val accessToken = codyAuthenticationManager.getTokenForAccount(defaultAccount) ?: ""
      return ServerAuth(
          defaultAccount.server.url, accessToken, defaultAccount.server.customRequestHeaders)
    }
    if (ConfigUtil.isIntegrationTestModeEnabled()) {
      val token = System.getenv("CODY_INTEGRATION_TEST_TOKEN")
      if (token != null) {
        return ServerAuth(ConfigUtil.DOTCOM_URL, token, "")
      } else {
        throw IllegalArgumentException(
            "Integration testing enabled but no CODY_INTEGRATION_TEST_TOKEN passed")
      }
    }
    return ServerAuth(ConfigUtil.DOTCOM_URL, "", "")
  }
}
