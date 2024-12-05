package com.sourcegraph.cody.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.auth.CodySecureStore.getFromSecureStore
import com.sourcegraph.cody.auth.CodySecureStore.writeToSecureStore

@Service(Service.Level.PROJECT)
class CodyAccountService(val project: Project) {
  private val activeAccountMarker = "active_cody_account"
  private val projectActiveAccountMarker = project.locationHash
  private var activeAccount: CodyAccount? = null

  @Volatile private var isActivated: Boolean = false

  fun isActivated(): Boolean {
    return isActivated
  }

  fun setActivated(isActivated: Boolean) {
    this.isActivated = isActivated
  }

  fun loadAccount(serverUrl: String): CodyAccount {
    val token = serverUrl.let { getFromSecureStore(it) }
    return CodyAccount(SourcegraphServerPath(serverUrl), token)
  }

  fun storeAccount(account: CodyAccount) {
    writeToSecureStore(account.server.url, account.token)
  }

  fun getActiveAccount(): CodyAccount? {
    if (activeAccount == null) {
      val serverUrl =
          getFromSecureStore(projectActiveAccountMarker) ?: getFromSecureStore(activeAccountMarker)
      activeAccount = if (serverUrl == null) null else loadAccount(serverUrl)
    }

    return activeAccount
  }

  fun setActiveAccount(account: CodyAccount) {
    storeAccount(account)
    writeToSecureStore(activeAccountMarker, account.server.url)
    writeToSecureStore(projectActiveAccountMarker, account.server.url)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CodyAccountService {
      return project.service<CodyAccountService>()
    }
  }
}
