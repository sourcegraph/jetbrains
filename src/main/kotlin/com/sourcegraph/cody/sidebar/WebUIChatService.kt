package com.sourcegraph.cody.sidebar

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.ExtensionMessage

@Service(Service.Level.PROJECT)
class WebUIChatService(private val project: Project) {
  companion object {
    // TODO: If not disposed, etc.
    @JvmStatic fun getInstance(project: Project): WebUIChatService = project.service<WebUIChatService>()
  }

  private var webUiProxy: WebUIProxy? = null

  fun setWebUiProxy(proxy: WebUIProxy) {
    webUiProxy = proxy
  }

  fun receiveMessage(message: ExtensionMessage) {
    webUiProxy?.postMessage(message)
  }
}