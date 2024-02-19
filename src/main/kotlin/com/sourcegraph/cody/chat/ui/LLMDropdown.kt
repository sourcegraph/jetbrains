package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ChatModelsParams
import com.sourcegraph.cody.agent.protocol.ChatModelsResponse
import com.sourcegraph.cody.chat.SessionId
import com.sourcegraph.cody.config.CodyAccount.Companion.isEnterpriseAccount
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.ui.ChatModel
import com.sourcegraph.cody.ui.LLMComboBoxItem
import com.sourcegraph.cody.ui.LLMComboBoxRenderer
import com.sourcegraph.common.BrowserOpener
import com.sourcegraph.common.CodyBundle
import java.util.concurrent.TimeUnit

class LLMDropdown(val project: Project, private val modelFromState: ChatModel?) :
    ComboBox<LLMComboBoxItem>(MutableCollectionComboBoxModel()) {

  private var firstMessageSent: Boolean = false

  init {
    renderer = LLMComboBoxRenderer(this)
    if (modelFromState != null) {
      addItem(LLMComboBoxItem(modelFromState.icon, modelFromState.displayName))
    }

    isEnabled = false
  }

  fun fetchAndUpdateModels(sessionId: SessionId) {
    if (modelFromState != null) {
      return
    }

    CodyAgentService.withAgent(project) { agent ->
      val chatModels = agent.server.chatModels(ChatModelsParams(sessionId))
      val response = chatModels.completeOnTimeout(null, 4, TimeUnit.SECONDS).get()
      val isCurrentUserPro = isCurrentUserPro(agent)
      (renderer as LLMComboBoxRenderer).isCurrentUserFree = !isCurrentUserPro
      ApplicationManager.getApplication().invokeLater { updateModels(response.models) }
    }
  }

  @RequiresEdt
  private fun updateModels(models: List<ChatModelsResponse.ChatModelProvider>) {
    removeAllItems()
    models.forEach { provider ->
      val model = ChatModel.fromAgentName(provider.model)
      val name = if (model == ChatModel.UNKNOWN_MODEL) provider.model else model.displayName
      addItem(LLMComboBoxItem(model.icon, name, provider.codyProOnly))
    }
    val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
    isEnabled = !firstMessageSent && !(activeAccountType.isEnterpriseAccount() || model.size <= 1)
  }

  override fun getModel(): MutableCollectionComboBoxModel<LLMComboBoxItem> {
    return super.getModel() as MutableCollectionComboBoxModel
  }

  override fun setSelectedItem(anObject: Any?) =
      CodyAgentService.withAgent(project) { agent ->
        val llmComboBoxItem = anObject as? LLMComboBoxItem
        if (llmComboBoxItem != null) {
          if (llmComboBoxItem.codyProOnly) {
            val isCurrentUserPro = isCurrentUserPro(agent)
            if (!isCurrentUserPro) {
              BrowserOpener.openInBrowser(project, "https://sourcegraph.com/cody/subscription")
              return@withAgent
            }
          }
        }

        super.setSelectedItem(anObject)
      }

  private fun isCurrentUserPro(agent: CodyAgent): Boolean =
      agent.server.isCurrentUserPro().completeOnTimeout(false, 4, TimeUnit.SECONDS).get() == true

  fun updateAfterFirstMessage() {
    firstMessageSent = true
    isEnabled = false

    val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
    if (activeAccountType?.isDotcomAccount() == true) {
      toolTipText = CodyBundle.getString("LLMDropdown.disabled.text")
    }
  }
}
