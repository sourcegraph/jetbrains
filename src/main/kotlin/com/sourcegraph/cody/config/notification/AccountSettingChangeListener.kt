package com.sourcegraph.cody.config.notification

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.statusbar.CodyAutocompleteStatusService
import com.sourcegraph.common.UpgradeToCodyProNotification
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.telemetry.GraphQlLogger

@Service(Service.Level.PROJECT)
class AccountSettingChangeListener(project: Project) : ChangeListener(project) {
  init {
    connection.subscribe(
        AccountSettingChangeActionNotifier.TOPIC,
        object : AccountSettingChangeActionNotifier {
          override fun beforeAction(serverUrlChanged: Boolean) {}

          override fun afterAction(context: AccountSettingChangeContext) {
            // Notify JCEF about the config changes
            javaToJSBridge?.callJS("pluginSettingsChanged", ConfigUtil.getConfigAsJson(project))

            // Notify Cody Agent about config changes.
            if (ConfigUtil.isCodyEnabled()) {
              CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
                agent.server.configurationDidChange(ConfigUtil.getAgentConfiguration(project))
              }
              CodyToolWindowContent.executeOnInstanceIfNotDisposed(project) {
                removeAllChatSessions()
              }
              CodyAgentService.getInstance(project).restartAgent(project)
            }

            UpgradeToCodyProNotification.autocompleteRateLimitError.set(null)
            UpgradeToCodyProNotification.chatRateLimitError.set(null)
            CodyAutocompleteStatusService.resetApplication(project)

            if (ConfigUtil.isCodyEnabled()) {
              CodyToolWindowContent.executeOnInstanceIfNotDisposed(project) {
                refreshSubscriptionTab()
              }
            }

            if (context.serverUrlChanged) {
              GraphQlLogger.logCodyEvent(project, "settings.serverURL", "changed")
            } else if (context.accessTokenChanged) {
              GraphQlLogger.logCodyEvent(project, "settings.accessToken", "changed")
            }
          }
        })
  }
}
