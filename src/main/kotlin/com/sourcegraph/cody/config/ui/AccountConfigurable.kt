package com.sourcegraph.cody.config.ui

import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.util.ui.EmptyIcon
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.auth.ui.customAccountsPanel
import com.sourcegraph.cody.config.*
import com.sourcegraph.cody.config.notification.AccountSettingChangeActionNotifier
import com.sourcegraph.cody.config.notification.AccountSettingChangeContext
import com.sourcegraph.cody.statusbar.CodyAutocompleteStatusService
import com.sourcegraph.common.UpgradeToCodyProNotification
import com.sourcegraph.config.ConfigUtil
import java.awt.Dimension

class AccountConfigurable(val project: Project) :
    BoundConfigurable(ConfigUtil.SOURCEGRAPH_DISPLAY_NAME) {
  private val accountManager = service<CodyAccountManager>()
  private val accountsModel = CodyAccountListModel(project)
  private val activeAccountHolder = project.service<CodyProjectActiveAccountHolder>()
  private lateinit var dialogPanel: DialogPanel
  private var channel: UpdateChannel = findConfiguredChannel()
  private val codyApplicationSettings = service<CodyApplicationSettings>()
  private val settingsModel =
      SettingsModel(shouldCheckForUpdates = codyApplicationSettings.shouldCheckForUpdates)

  override fun createPanel(): DialogPanel {
    dialogPanel = panel {
      group("Authentication") {
        row {
          customAccountsPanel(
                  accountManager,
                  activeAccountHolder,
                  accountsModel,
                  CodyAccountDetailsProvider(
                      ProgressIndicatorsProvider().also { Disposer.register(disposable!!, it) },
                      accountManager,
                      accountsModel),
                  disposable!!,
                  true,
                  EmptyIcon.ICON_16) {
                    it.copy(server = it.server.copy())
                  }
              .horizontalAlign(HorizontalAlign.FILL)
              .verticalAlign(VerticalAlign.FILL)
              .applyToComponent { this.preferredSize = Dimension(Int.MAX_VALUE, 200) }
              .also {
                DataManager.registerDataProvider(it.component) { key ->
                  if (CodyAccountsHost.DATA_KEY.`is`(key)) accountsModel else null
                }
              }
        }
      }

      group("Plugin") {
        row {
          label("Update channel:")
          comboBox(
                  UpdateChannel.values().toList(),
                  SimpleListCellRenderer.create("") { it.presentableText })
              .bindItem({ channel }, { channel = it!! })
        }
        row {
          checkBox("Automatically check for plugin updates")
              .bindSelected(settingsModel::shouldCheckForUpdates)
        }
      }
    }
    return dialogPanel
  }

  override fun reset() {
    dialogPanel.reset()
    codyApplicationSettings.shouldCheckForUpdates = settingsModel.shouldCheckForUpdates
  }

  override fun apply() {
    val bus = project.messageBus
    val publisher = bus.syncPublisher(AccountSettingChangeActionNotifier.TOPIC)

    val oldActiveAccount = activeAccountHolder.account
    val oldUrl = oldActiveAccount?.server?.url ?: ""

    var activeAccount = accountsModel.activeAccount
    val activeAccountRemoved = !accountsModel.accounts.contains(activeAccount)
    if (activeAccountRemoved || activeAccount == null) {
      activeAccount = accountsModel.accounts.getFirstAccountOrNull()
    }
    val newAccessToken = activeAccount?.let { accountsModel.newCredentials[it] }

    val activeAccountChanged = oldActiveAccount != activeAccount
    val accessTokenChanged = newAccessToken != null || activeAccountRemoved || activeAccountChanged

    val newUrl = activeAccount?.server?.url ?: ""

    val serverUrlChanged = oldUrl != newUrl

    publisher.beforeAction(serverUrlChanged)
    super.apply()
    val context = AccountSettingChangeContext(serverUrlChanged, accessTokenChanged)
    CodyAuthenticationManager.instance.setActiveAccount(project, activeAccount)
    accountsModel.activeAccount = activeAccount
    publisher.afterAction(context)

    codyApplicationSettings.shouldCheckForUpdates = settingsModel.shouldCheckForUpdates
    if (codyApplicationSettings.shouldCheckForUpdates) {
      CheckUpdatesTask(project).queue()
    }

    applyChannelConfiguration()

    // todo: create som pub & sub mechanism for these
    UpgradeToCodyProNotification.autocompleteRateLimitError.set(null)
    UpgradeToCodyProNotification.chatRateLimitError.set(null)
    CodyAutocompleteStatusService.resetApplication(project)

    ApplicationManager.getApplication().executeOnPooledThread {
      CodyToolWindowContent.getInstance(project).refreshSubscriptionTab()
    }
  }

  private fun applyChannelConfiguration() {
    val configuredChannel = findConfiguredChannel()
    val newChannel = channel

    if (!configuredChannel.equals(newChannel)) {
      if (!UpdateChannel.Stable.equals(configuredChannel)) {
        UpdateSettings.getInstance().storedPluginHosts.remove(configuredChannel.channelUrl)
      }

      if (!UpdateChannel.Stable.equals(newChannel)) {
        UpdateSettings.getInstance().storedPluginHosts.add(newChannel.channelUrl)
      }
    }
  }

  private fun findConfiguredChannel(): UpdateChannel {
    var currentChannel = UpdateChannel.Stable
    for (channel in UpdateChannel.values()) {
      val url = channel.channelUrl
      if (url != null && UpdateSettings.getInstance().storedPluginHosts.contains(url)) {
        currentChannel = channel
        break
      }
    }
    return currentChannel
  }
}
