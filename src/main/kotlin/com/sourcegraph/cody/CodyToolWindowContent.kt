package com.sourcegraph.cody

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.ui.web.WebUIService
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
class CodyToolWindowContent(project: Project) {
  val allContentPanel: JComponent = JPanel(CardLayout())
  private val mainPanel = JPanel(GridBagLayout())
  private var webviewPanel: JComponent? = null

  init {
    val spinnerLabel =
        JBLabel("Starting Cody...", Icons.StatusBar.CompletionInProgress, JBLabel.CENTER)
    mainPanel.add(spinnerLabel, GridBagConstraints())
    WebUIService.getInstance(project).views.provideCodyToolWindowContent(this)
    refreshPanelsVisibility()
  }

  @RequiresEdt
  fun refreshPanelsVisibility() {
    val component = webviewPanel ?: mainPanel
    if (allContentPanel.components.isEmpty() || allContentPanel.getComponent(0) != component) {
      allContentPanel.removeAll()
      allContentPanel.add(component)
    }
  }

  /** Sets the webview component to display, if any. */
  @RequiresEdt
  fun setWebviewComponent(component: JComponent?) {
    webviewPanel = component
    refreshPanelsVisibility()
  }

  companion object {
    var logger = Logger.getInstance(CodyToolWindowContent::class.java)

    fun executeOnInstanceIfNotDisposed(
        project: Project,
        myAction: CodyToolWindowContent.() -> Unit
    ) {
      UIUtil.invokeLaterIfNeeded {
        if (!project.isDisposed) {
          val codyToolWindowContent = project.getService(CodyToolWindowContent::class.java)
          codyToolWindowContent.myAction()
        }
      }
    }
  }
}
