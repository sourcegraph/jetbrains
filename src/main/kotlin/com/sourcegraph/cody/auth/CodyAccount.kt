package com.sourcegraph.cody.auth

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.sourcegraph.cody.api.SourcegraphApiRequestExecutor
import com.sourcegraph.cody.api.SourcegraphApiRequests
import com.sourcegraph.config.ConfigUtil
import java.util.concurrent.CompletableFuture

data class CodyAccount(val server: SourcegraphServerPath) {

  @Volatile var isFreeTier: CompletableFuture<Boolean> = CompletableFuture()

  fun isDotcomAccount(): Boolean = server.url.lowercase().startsWith(ConfigUtil.DOTCOM_URL)

  fun isEnterpriseAccount(): Boolean = isDotcomAccount().not()

  fun getToken(): String? {
    return PasswordSafe.instance.get(credentialAttributes(server.url))?.getPasswordAsString()
  }

  fun storeToken(token: String?) {
    PasswordSafe.instance.set(credentialAttributes(server.url), Credentials(user = "", token))
    isFreeTier = isCurrentUserFree()
  }

  private fun isCurrentUserFree(): CompletableFuture<Boolean> {
    val token = getToken() ?: return CompletableFuture.completedFuture(true)
    val progressIndicator = EmptyProgressIndicator(ModalityState.nonModal())
    return service<ProgressManager>()
        .submitIOTask(progressIndicator) {
          runCatching {
            SourcegraphApiRequests.CurrentUser(
                    SourcegraphApiRequestExecutor.Factory.instance.create(server, token),
                    progressIndicator)
                .getCodyProEnabled()
          }
        }
        .successOnEdt(progressIndicator.modalityState) { isProEnabled ->
          isProEnabled.getOrNull()?.codyProEnabled ?: true
        }
  }

  companion object {
    private const val ACTIVE_ACCOUNT_MARKER = "active_cody_account"

    private fun credentialAttributes(key: String): CredentialAttributes =
        CredentialAttributes(generateServiceName("Sourcegraph", key))

    fun hasActiveAccount(): Boolean {
      return getActiveAccount() != null
    }

    fun getActiveAccount(): CodyAccount? {
      val serverUrl =
          PasswordSafe.instance
              .get(credentialAttributes(ACTIVE_ACCOUNT_MARKER))
              ?.getPasswordAsString()
      return if (serverUrl == null) null else CodyAccount(SourcegraphServerPath(serverUrl))
    }

    fun setActiveAccount(account: CodyAccount) {
      PasswordSafe.instance.set(
          credentialAttributes(ACTIVE_ACCOUNT_MARKER), Credentials(user = "", account.server.url))
    }
  }
}
