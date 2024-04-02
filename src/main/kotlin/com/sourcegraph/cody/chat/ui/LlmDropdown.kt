package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ChatModelsParams
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.agent.protocol.ModelUsage
import com.sourcegraph.cody.config.AccountTier
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.state.LLMState
import com.sourcegraph.cody.ui.LlmComboBoxRenderer
import com.sourcegraph.common.BrowserOpener
import com.sourcegraph.common.CodyBundle
import java.util.concurrent.TimeUnit

class LlmDropdown(
    private val modelUsage: ModelUsage,
    private val project: Project,
    private val onSetSelectedItem: (ChatModelsResponse.ChatModelProvider) -> Unit,
    private val chatModelProviderFromState: ChatModelsResponse.ChatModelProvider?,
) : ComboBox<ChatModelsResponse.ChatModelProvider>(MutableCollectionComboBoxModel()) {

  var isCurrentUserFree = true

  init {
    renderer = LlmComboBoxRenderer(this)
    if (chatModelProviderFromState != null) {
      addItem(chatModelProviderFromState)
    }

    isVisible = false
    updateModels()
  }

  private fun updateModels() {
    if (chatModelProviderFromState != null) {
      return
    }

    CodyAgentService.withAgent(project) { agent ->
      val chatModels = agent.server.chatModels(ChatModelsParams(modelUsage.value))
      val response =
          chatModels.completeOnTimeout(null, 10, TimeUnit.SECONDS).get() ?: return@withAgent

      invokeLater { updateModelsInUI(response.models) }
    }
  }

  @RequiresEdt
  private fun updateModelsInUI(models: List<ChatModelsResponse.ChatModelProvider>) {
    removeAllItems()
    models.sortedBy { it.codyProOnly }.forEach(::addItem)

    val selectedModel = HistoryService.getInstance(project).getDefaultLlm()
    val defaultModel =
        models.find { it.model == selectedModel?.model } ?: models.find { it.default }
    defaultModel?.let { this.selectedItem = it }

    CodyAuthenticationManager.getInstance(project).getActiveAccountTier().thenApply { accountTier ->
      isCurrentUserFree = accountTier == AccountTier.DOTCOM_FREE
    }

    val isEnterpriseAccount =
        CodyAuthenticationManager.getInstance(project).getActiveAccount()?.isEnterpriseAccount()
            ?: false

    if (model.size <= 1) isEnabled = false
    isVisible = !isEnterpriseAccount
    revalidate()
  }

  override fun getModel(): MutableCollectionComboBoxModel<ChatModelsResponse.ChatModelProvider> {
    return super.getModel() as MutableCollectionComboBoxModel
  }

  override fun setSelectedItem(anObject: Any?) {
    val modelProvider = anObject as? ChatModelsResponse.ChatModelProvider
    if (modelProvider != null) {
      if (modelProvider.codyProOnly && isCurrentUserFree) {
        BrowserOpener.openInBrowser(project, "https://sourcegraph.com/cody/subscription")
        return
      }

      HistoryService.getInstance(project).setDefaultLlm(LLMState.fromChatModel(modelProvider))

      super.setSelectedItem(anObject)
      onSetSelectedItem(modelProvider)
    }
  }

  @RequiresEdt
  fun updateAfterFirstMessage() {
    isEnabled = false

    val activeAccountType = CodyAuthenticationManager.getInstance(project).getActiveAccount()
    if (activeAccountType?.isDotcomAccount() == true) {
      toolTipText = CodyBundle.getString("LlmDropdown.disabled.text")
    }
  }
}
