package com.sourcegraph.cody.edit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.config.ConfigUtil

open class NonInteractiveEditCommandAction(runAction: (Editor, FixupService) -> Unit) :
    EditCommandAction(runAction) {
  override fun update(event: AnActionEvent) {
    super.update(event)

    val project = event.project ?: return
    val hasActiveAccount = CodyAuthenticationManager.getInstance(project).hasActiveAccount()
    event.presentation.isEnabled =
      // TODO: This is a hack to enable the action in tests.
      //  - Need to investigate why we don't have an active account.
      //  - CodyAuthenticationManager needs to use the CODY_INTEGRATION_TEST_TOKEN
        (ConfigUtil.isIntegrationTestModeEnabled() || hasActiveAccount) &&
            !FixupService.getInstance(project).isEditInProgress()
    if (!event.presentation.isEnabled) {
      event.presentation.description =
          CodyBundle.getString("action.sourcegraph.disabled.description")
    }
  }
}
