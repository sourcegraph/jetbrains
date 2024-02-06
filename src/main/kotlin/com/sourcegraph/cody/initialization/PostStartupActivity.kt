package com.sourcegraph.cody.initialization

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.sourcegraph.cody.CodyFocusChangeListener
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.auth.SelectOneOfTheAccountsAsActive
import com.sourcegraph.cody.config.SettingsMigration
import com.sourcegraph.cody.config.ui.CheckUpdatesTask
import com.sourcegraph.cody.statusbar.CodyAutocompleteStatusService
import com.sourcegraph.config.CodyAuthNotificationActivity
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.telemetry.TelemetryInitializerActivity

/**
 * StartupActivity is obsolete in recent platform versions.
 *
 * TODO: We should migrate to com.intellij.openapi.startup.ProjectActivity when we bump
 *   compatibility.
 */
class PostStartupActivity : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    TelemetryInitializerActivity().runActivity(project)
    SettingsMigration().runActivity(project)
    SelectOneOfTheAccountsAsActive().runActivity(project)
    CodyAuthNotificationActivity().runActivity(project)
    CheckUpdatesTask(project).queue()
    if (ConfigUtil.isCodyEnabled()) CodyAgentService.getInstance(project).startAgent(project)
    CodyAutocompleteStatusService.resetApplication(project)
    CodyFocusChangeListener().runActivity(project)
    CodyAgentCodebase.getInstance(project).onFileOpened(project, null)
    EndOfTrialNotificationScheduler.createAndStart(project)
  }
}
