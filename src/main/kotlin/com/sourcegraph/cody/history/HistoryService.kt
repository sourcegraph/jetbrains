package com.sourcegraph.cody.history

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.auth.deprecated.DeprecatedCodyAccountManager
import com.sourcegraph.cody.history.state.AccountData
import com.sourcegraph.cody.history.state.ChatState
import com.sourcegraph.cody.history.state.HistoryState
import com.sourcegraph.cody.history.state.LLMState

@State(name = "ChatHistory", storages = [Storage("cody_history.xml")])
@Service(Service.Level.PROJECT)
class HistoryService : SimplePersistentStateComponent<HistoryState>(HistoryState()) {

  @Synchronized
  fun getDefaultLlm(): LLMState? {
    val account = DeprecatedCodyAccountManager.getInstance().account
    val llm = account?.let { findEntry(it.id) }?.defaultLlm
    if (llm == null) return null
    return LLMState().also { it.copyFrom(llm) }
  }

  @Synchronized
  fun setDefaultLlm(defaultLlm: LLMState) {
    val newDefaultLlm = LLMState()
    newDefaultLlm.copyFrom(defaultLlm)
    getOrCreateActiveAccountEntry().defaultLlm = newDefaultLlm
  }

  @Synchronized
  fun getChatHistoryFor(accountId: String): List<ChatState>? = findEntry(accountId)?.chats

  private fun findEntry(accountId: String): AccountData? =
      state.accountData.find { it.accountId == accountId }

  private fun getOrCreateActiveAccountEntry(): AccountData {
    val activeAccount =
        DeprecatedCodyAccountManager.getInstance().account
            ?: throw IllegalStateException("No active account")

    val existingEntry = findEntry(activeAccount.id)
    return existingEntry ?: AccountData(activeAccount.id).also { state.accountData += it }
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): HistoryService = project.service<HistoryService>()
  }
}
