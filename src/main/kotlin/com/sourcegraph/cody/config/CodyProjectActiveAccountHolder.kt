package com.sourcegraph.cody.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.auth.PersistentActiveAccountHolder

@State(
    name = "CodyActiveAccount",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
    reportStatistic = false)
@Service(Service.Level.PROJECT)
class CodyProjectActiveAccountHolder(val project: Project) :
    PersistentActiveAccountHolder<CodyAccount>() {

  override fun accountManager() = service<CodyAccountManager>()

  override fun loadState(state: AccountState) {
    val newAccount =
        state.activeAccountId?.let { id -> accountManager().accounts.find { it.id == id } }
            ?: accountManager().accounts.getFirstAccountOrNull()

    CodyAuthenticationManager.getInstance(project).setActiveAccount(newAccount)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CodyProjectActiveAccountHolder {
      return project.service<CodyProjectActiveAccountHolder>()
    }
  }
}
