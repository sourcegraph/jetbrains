package com.sourcegraph.cody.initialization

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.auth.SelectOneOfTheAccountsAsActive
import com.sourcegraph.cody.config.SettingsMigration
import com.sourcegraph.cody.config.ui.CheckUpdatesTask
import com.sourcegraph.cody.listeners.CodyCaretListener
import com.sourcegraph.cody.listeners.CodyDocumentListener
import com.sourcegraph.cody.listeners.CodyFocusChangeListener
import com.sourcegraph.cody.listeners.CodySelectionListener
import com.sourcegraph.cody.statusbar.CodyStatusService
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
    CodyStatusService.resetApplication(project)
    EndOfTrialNotificationScheduler.createAndStart(project)

    val multicaster = EditorFactory.getInstance().eventMulticaster
    if (multicaster is EditorEventMulticasterEx) {
      try {
        val disposable = CodyAgentService.getInstance(project)
        multicaster.addFocusChangeListener(CodyFocusChangeListener(), disposable)
        multicaster.addCaretListener(CodyCaretListener(), disposable)
        multicaster.addSelectionListener(CodySelectionListener(), disposable)
        multicaster.addDocumentListener(CodyDocumentListener(project), disposable)
      } catch (e: Exception) {
        // Ignore exception https://github.com/sourcegraph/sourcegraph/issues/56032
      }
    }
  }
}
