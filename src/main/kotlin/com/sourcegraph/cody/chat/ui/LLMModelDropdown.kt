package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ChatModelsParams
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.chat.SessionId
import com.sourcegraph.cody.config.CodyAccount.Companion.isEnterpriseAccount
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.ui.ChatModel
import com.sourcegraph.cody.ui.LLMModelComboBoxItem
import com.sourcegraph.cody.ui.LLMModelComboBoxRenderer
import com.sourcegraph.common.BrowserOpener
import java.util.concurrent.TimeUnit

class LLMModelDropdown(val project: Project, initiallySelected: ChatModel?) :
    ComboBox<LLMModelComboBoxItem>(MutableCollectionComboBoxModel()) {

  init {
    val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
    if (activeAccountType.isEnterpriseAccount()) {
      isEnabled = false
    }

    renderer = LLMModelComboBoxRenderer()
    if (initiallySelected != null) {
      addItem(LLMModelComboBoxItem(initiallySelected.icon, initiallySelected.displayName))
    }
  }

  fun fetchAndUpdateModels(sessionId: SessionId) {
    CodyAgentService.withAgent(project) { agent ->
      val chatModels = agent.server.chatModels(ChatModelsParams(sessionId))
      val response = chatModels.completeOnTimeout(null, 4, TimeUnit.SECONDS).get()
      ApplicationManager.getApplication().invokeLater { updateModels(response.models) }
    }
  }

  @RequiresEdt
  private fun updateModels(models: List<ChatModelsResponse.ChatModelProvider>) {
    removeAllItems()
    models.forEach { provider ->
      val model = ChatModel.fromAgentName(provider.model)
      val name = if (model == ChatModel.UNKNOWN_MODEL) provider.model else model.displayName
      addItem(LLMModelComboBoxItem(model.icon, name, provider.codyProOnly))
    }
  }

  override fun getModel(): MutableCollectionComboBoxModel<LLMModelComboBoxItem> {
    return super.getModel() as MutableCollectionComboBoxModel
  }

  override fun setSelectedItem(anObject: Any?) =
      CodyAgentService.withAgent(project) { agent ->
        val llmModelComboBoxItem = anObject as? LLMModelComboBoxItem
        if (llmModelComboBoxItem != null) {
          if (llmModelComboBoxItem.codyProOnly) {
            val isCurrentUserPro = agent.server.isCurrentUserPro().get()
            if (!isCurrentUserPro) {
              BrowserOpener.openInBrowser(project, "https://sourcegraph.com/cody/subscription")
              return@withAgent
            }
          }
        }

        super.setSelectedItem(anObject)
      }
}
