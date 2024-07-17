package com.sourcegraph.cody.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerListener
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.WebviewResolveWebviewViewParams
import com.sourcegraph.cody.agent.protocol.WebviewCreateWebviewPanelOptions
import com.sourcegraph.cody.agent.protocol.WebviewCreateWebviewPanelParams

class WebUIToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    WebviewViewService.getInstance(project).provideToolWindow(toolWindow)
  }
}

// Responsibilities:
// - Rendezvous between ToolWindows implementing "Views" (Tool Windows in JetBrains), and WebviewViews.
@Service(Service.Level.PROJECT)
class WebviewViewService(val project: Project) {
  // Map of "view ID" to hosting ToolWindow.
  private val views: MutableMap<String, ToolWindow> = mutableMapOf()
  private val providers: MutableMap<String, Provider> = mutableMapOf()

  data class Provider(
    val id: String,
    val options: ProviderOptions,
  )

  data class ProviderOptions(
    val retainContextWhenHidden: Boolean,
  )

  fun registerProvider(id: String, retainContextWhenHidden: Boolean) {
    var provider = Provider(id, ProviderOptions(retainContextWhenHidden))
    providers[id] = provider
    val toolWindow = views[id] ?: return
    runInEdt { provideView(toolWindow, provider) }
  }

  // TODO: Implement 'dispose' for registerWebviewViewProvider.

  fun provideToolWindow(toolWindow: ToolWindow) {
    views[toolWindow.id] = toolWindow
    val provider = providers[toolWindow.id] ?: return
    runInEdt { provideView(toolWindow, provider) }
  }

  private fun provideView(toolWindow: ToolWindow, provider: Provider) {
    toolWindow.isAvailable = true

    val handle = "native-webview-view-${toolWindow.id}"
    WebUIService.getInstance(project).createWebviewView(handle) { proxy ->
      val lockable = true
      val content = ContentFactory.SERVICE.getInstance()
        .createContent(proxy.component, proxy.title, lockable)
      toolWindow.contentManager.addContent(content)
      return@createWebviewView object : WebviewViewDelegate {
        override fun setTitle(newTitle: String) {
          runInEdt {
            content.displayName = newTitle
          }
        }
        // TODO: Add icon support.
      }
    }

    CodyAgentService.withAgent(project) {
      // TODO: https://code.visualstudio.com/api/references/vscode-api#WebviewViewProvider
      it.server.webviewResolveWebviewView(WebviewResolveWebviewViewParams(viewId = provider.id, webviewHandle = handle))
    }
  }

  // TODO: Consider moving this to a separate class.
  fun createPanel(proxy: WebUIProxy, params: WebviewCreateWebviewPanelParams): WebviewViewDelegate? {
    // TODO: Give these files unique names.
    val file = LightVirtualFile("WebPanel")
    file.fileType = WebPanelFileType.INSTANCE
    file.putUserData(WebPanelTabTitleProvider.WEB_PANEL_TITLE_KEY, params.title)
    file.putUserData(WebPanelEditor.WEBVIEW_COMPONENT_KEY, proxy.component)
    // TODO: Hang onto this editor to dispose of it, etc.
    FileEditorManager.getInstance(project).openFile(file, !params.showOptions.preserveFocus)
    return object : WebviewViewDelegate {
      override fun setTitle(newTitle: String) {
        runInEdt {
          file.putUserData(WebPanelTabTitleProvider.WEB_PANEL_TITLE_KEY, newTitle)
          FileEditorManager.getInstance(project).updateFilePresentation(file)
        }
      }
      // TODO: Add icon support.
    }
  }

  companion object {
    fun getInstance(project: Project): WebviewViewService {
      return project.service()
    }
  }
}

interface WebviewViewDelegate {
  fun setTitle(newTitle: String)
}
