package com.sourcegraph.cody.ui.web

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.CodyToolWindowContent
import com.sourcegraph.cody.CodyToolWindowContent.Companion.MAIN_PANEL
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.WebviewResolveWebviewViewParams
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel

/// A view that can host a browser component.
private interface WebviewHost {
  /// The provider ID of this view.
  val id: String
  val viewDelegate: WebviewViewDelegate

  /// Adopts a Webview into this host.
  fun adopt(proxy: WebUIProxy)

  // Resets the webview host to its pre-adopted state.
  @RequiresEdt fun reset()
}

private class CodyToolWindowContentWebviewHost(
    private val owner: CodyToolWindowContent,
    val placeholder: JComponent
) : WebviewHost {
  override val id = "cody.chat"

  var proxy: WebUIProxy? = null

  override val viewDelegate =
      object : WebviewViewDelegate {
        override fun setTitle(newTitle: String) {
          // No-op.
        }
      }

  override fun adopt(proxy: WebUIProxy) {
    runInEdt {
      assert(this.proxy == null)
      this.proxy = proxy
      swapMainPanel(remove = placeholder, insert = proxy.component)
    }
  }

  @RequiresEdt
  override fun reset() {
    assert(ApplicationManager.getApplication().isDispatchThread)
    swapMainPanel(remove = proxy?.component, insert = placeholder)
    this.proxy = null
  }

  private fun swapMainPanel(remove: JComponent?, insert: JComponent?) {
    remove?.let { owner.allContentPanel.remove(it) }
    insert?.let {
      owner.allContentPanel.add(it, MAIN_PANEL, CodyToolWindowContent.MAIN_PANEL_INDEX)
    }
    owner.refreshPanelsVisibility()
  }
}

// Responsibilities:
// - Rendezvous between ToolWindows implementing "Views" (Tool Windows in JetBrains), and
// WebviewViews.
internal class WebviewViewManager(private val project: Project) {
  // Map of "view ID" to a host.
  private val views: MutableMap<String, WebviewHost> = mutableMapOf()
  private val providers: MutableMap<String, Provider> = mutableMapOf()

  private data class Provider(
      val id: String,
      val options: ProviderOptions,
  )

  private data class ProviderOptions(
      val retainContextWhenHidden: Boolean,
  )

  fun reset(): CompletableFuture<Void> {
    val viewsToReset = mutableListOf<WebviewHost>()
    synchronized(providers) {
      viewsToReset.addAll(views.values)
      views.clear()
      providers.clear()
    }
    val result = CompletableFuture<Void>()
    runInEdt {
      viewsToReset.forEach { it.reset() }
      result.complete(null)
    }
    return result
  }

  fun registerProvider(id: String, retainContextWhenHidden: Boolean) {
    val viewHost: WebviewHost
    val provider = Provider(id, ProviderOptions(retainContextWhenHidden))
    synchronized(providers) {
      providers[id] = provider
      viewHost = views[id] ?: return
    }
    runInEdt { provideView(viewHost, provider) }
  }

  // TODO: Implement 'dispose' for registerWebviewViewProvider.

  private fun provideHost(viewHost: WebviewHost) {
    val provider: Provider
    synchronized(providers) {
      views[viewHost.id] = viewHost
      provider = providers[viewHost.id] ?: return
    }
    runInEdt { provideView(viewHost, provider) }
  }

  fun provideCodyToolWindowContent(codyContent: CodyToolWindowContent) {
    // Because the webview may be created lazily, populate a placeholder control.
    val placeholder = JPanel(GridBagLayout())
    val spinnerLabel =
        JBLabel("Starting Cody...", Icons.StatusBar.CompletionInProgress, JBLabel.CENTER)
    placeholder.add(spinnerLabel, GridBagConstraints())

    codyContent.allContentPanel.add(placeholder, MAIN_PANEL, CodyToolWindowContent.MAIN_PANEL_INDEX)
    provideHost(CodyToolWindowContentWebviewHost(codyContent, placeholder))
  }

  private fun provideView(viewHost: WebviewHost, provider: Provider) {
    val handle = "native-webview-view-${viewHost.id}"
    WebUIService.getInstance(project).createWebviewView(handle) { proxy ->
      viewHost.adopt(proxy)
      return@createWebviewView viewHost.viewDelegate
    }

    CodyAgentService.withAgent(project) {
      // TODO: https://code.visualstudio.com/api/references/vscode-api#WebviewViewProvider
      it.server.webviewResolveWebviewView(
          WebviewResolveWebviewViewParams(viewId = provider.id, webviewHandle = handle))
    }
  }
}
