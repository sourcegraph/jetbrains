package com.sourcegraph.cody.config

import com.intellij.openapi.project.Project
import com.sourcegraph.cody.statusbar.CodyAutocompleteStatusService

object RateLimitStateManager {

  fun invalidateForChat(project: Project) {
    if (CodyApplicationSettings.instance.chatRateLimitError) {
      CodyApplicationSettings.instance.chatRateLimitError = false
      CodyAutocompleteStatusService.resetApplication(project)
    }
  }

  fun reportForChat(project: Project) {
    if (!CodyApplicationSettings.instance.chatRateLimitError) {
      CodyApplicationSettings.instance.chatRateLimitError = true
      CodyAutocompleteStatusService.resetApplication(project)
    }
  }

}
