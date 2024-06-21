package com.sourcegraph.cody.config

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.Key

interface CodyAccountsHost {
  fun addAccount(
      server: SourcegraphServerPath,
      login: String,
      displayName: String?,
      token: String,
      id: String
  )

  companion object {
    val DATA_KEY: DataKey<CodyAccountsHost> = DataKey.create("CodyAccountsHots")
    val KEY: Key<CodyAccountsHost> = Key.create("CodyAccountsHots")
  }
}
