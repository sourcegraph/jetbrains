package com.sourcegraph.cody.auth

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "CodyAccounts",
    storages =
        [
            Storage(value = "cody_accounts.xml"),
        ],
    reportStatistic = false,
    category = SettingsCategory.TOOLS)
class CodyPersistentAccounts : PersistentStateComponent<Array<CodyAccount>> {
  private var state = emptyArray<CodyAccount>()

  var accounts: Set<CodyAccount>
    get() = state.toSet()
    set(value) {
      state = value.toTypedArray()
    }

  override fun getState(): Array<CodyAccount> = state

  override fun loadState(state: Array<CodyAccount>) {
    this.state = state
  }
}
