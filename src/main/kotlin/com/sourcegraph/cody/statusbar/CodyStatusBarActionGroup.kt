package com.sourcegraph.cody.statusbar

import com.intellij.ide.actions.AboutAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.sourcegraph.config.ConfigUtil

class CodyStatusBarActionGroup : DefaultActionGroup() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = ConfigUtil.isCodyEnabled()

    removeAll()
    if (CodyAutocompleteStatusService.getCurrentStatus() ==
        CodyAutocompleteStatus.CodyAgentNotRunning) {
      addAll(
          listOf(
              OpenLogAction(),
              AboutAction().apply {
                templatePresentation.text = "Open About To Troubleshoot Issue"
              },
              ReportCodyBugAction()))
    } else {
      addAll(
          listOfNotNull(
              ChatAndAutocompleteLimitWarningAction(),
              ChatLimitWarningAction(),
              AutocompletionLimitWarningAction(),
          ))
      addSeparator()
      addAll(
          listOfNotNull(
              CodyDisableAutocompleteAction(),
              CodyEnableLanguageForAutocompleteAction(),
              CodyDisableLanguageForAutocompleteAction(),
              CodyManageAccountsAction(),
              CodyOpenSettingsAction(),
          ))
    }
  }
}
