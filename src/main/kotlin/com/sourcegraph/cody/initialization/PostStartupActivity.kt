package com.sourcegraph.cody.initialization

import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.sourcegraph.cody.CodyFileEditorListener
import com.sourcegraph.cody.agent.CodyAgentManager
import com.sourcegraph.cody.agent.CodyAgentServer
import com.sourcegraph.cody.auth.SelectOneOfTheAccountsAsActive
import com.sourcegraph.cody.config.SettingsMigration
import com.sourcegraph.cody.config.ui.CheckUpdatesTask
import com.sourcegraph.cody.context.CurrentlyOpenFileListener
import com.sourcegraph.cody.statusbar.CodyAutocompleteStatusService
import com.sourcegraph.config.CodyAuthNotificationActivity
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.telemetry.TelemetryInitializerActivity
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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
    if (ConfigUtil.isCodyEnabled()) {
      System.err.println("[głupie_logi] CodyAgentManager.startAgent(project)");
      CodyAgentManager.startAgent(project)
    }
    CodyAutocompleteStatusService.resetApplication(project)

    project.messageBus
            .connect()
            .subscribe(
                    FileEditorManagerListener.FILE_EDITOR_MANAGER, CodyFileEditorListener())

  }
}
