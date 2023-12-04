package com.sourcegraph.cody.statusbar

import com.intellij.ide.actions.AboutAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.sourcegraph.cody.config.CodyApplicationSettings
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

      val warningActions = createWarningActions()

      addAll(listOfNotNull(warningActions))
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

  private fun createWarningActions() =
      if (CodyApplicationSettings.instance.autocompleteRateLimitError &&
          CodyApplicationSettings.instance.chatRateLimitError) {
        RateLimitErrorWarningAction(
            "<html><b>Warning:</b> Chat and Autocomplete Limit Reached...</html>",
            "You've used all messages and autocompletions. The allowed number of request per day is limited at the moment to ensure the service stays functional.",
            "Chat and Autocomplete Limit Reached",
        )
      } else if (CodyApplicationSettings.instance.autocompleteRateLimitError) {
        RateLimitErrorWarningAction(
            "<html><b>Warning:</b> Autocomplete Limit Reached...</html>",
            "You've used all autocompletions. The allowed number of request per day is limited at the moment to ensure the service stays functional.",
            "Autocomplete Limit Reached",
        )
      } else if (CodyApplicationSettings.instance.chatRateLimitError) {
        RateLimitErrorWarningAction(
            "<html><b>Warning:</b> Chat Limit Reached...</html>",
            "You've used all messages. The allowed number of request per day is limited at the moment to ensure the service stays functional.",
            "Chat Limit Reached",
        )
      } else {
        null
      }
}
