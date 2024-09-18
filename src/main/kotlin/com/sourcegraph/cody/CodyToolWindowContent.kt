package com.sourcegraph.cody

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.chat.SignInWithSourcegraphPanel
import com.sourcegraph.cody.chat.ui.CodyOnboardingGuidancePanel
import com.sourcegraph.cody.config.CodyAccount
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.ui.web.WebUIService
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
class CodyToolWindowContent(project: Project) {
  private val cardLayout = CardLayout()
  private val cardPanel = JPanel(cardLayout)
  val allContentPanel: JComponent = JPanel(GridLayout(1, 1))
  private var webviewPanel: JComponent? = null

  init {
    cardPanel.add(SignInWithSourcegraphPanel(project), SIGN_IN_PANEL, SIGN_IN_PANEL_INDEX)
    val codyOnboardingGuidancePanel = CodyOnboardingGuidancePanel(project)
    codyOnboardingGuidancePanel.addMainButtonActionListener {
      CodyApplicationSettings.instance.isOnboardingGuidanceDismissed = true
      refreshPanelsVisibility()
    }
    cardPanel.add(codyOnboardingGuidancePanel, ONBOARDING_PANEL, ONBOARDING_PANEL_INDEX)

    // Because the webview may be created lazily, populate a placeholder control.
    val placeholder = JPanel(GridBagLayout())
    val spinnerLabel =
      JBLabel("Starting Cody...", Icons.StatusBar.CompletionInProgress, JBLabel.CENTER)
    placeholder.add(spinnerLabel, GridBagConstraints())
    cardPanel.add(placeholder, LOADING_PANEL, LOADING_PANEL_INDEX)

    WebUIService.getInstance(project).views.provideCodyToolWindowContent(this)

    refreshPanelsVisibility()
  }

  @RequiresEdt
  fun refreshPanelsVisibility() {
    val codyAuthenticationManager = CodyAuthenticationManager.getInstance()
    if (codyAuthenticationManager.hasNoActiveAccount() ||
        codyAuthenticationManager.showInvalidAccessTokenError()) {
      cardLayout.show(cardPanel, SIGN_IN_PANEL)
      showView(cardPanel)
      return
    }
    val activeAccount = codyAuthenticationManager.account
    if (!CodyApplicationSettings.instance.isOnboardingGuidanceDismissed) {
      val displayName = activeAccount?.let(CodyAccount::displayName)
      cardPanel.getComponent(ONBOARDING_PANEL_INDEX)?.let {
        (it as CodyOnboardingGuidancePanel).updateDisplayName(displayName)
      }
      cardLayout.show(cardPanel, ONBOARDING_PANEL)
      showView(cardPanel)
      return
    }
    cardLayout.show(cardPanel, LOADING_PANEL)
    showView(webviewPanel ?: cardPanel)
  }

  // Flips the sidebar view to the specified top level component. We do it this way
  // because JetBrains Remote does not display webviews inside a component using
  // CardLayout.
  private fun showView(component: JComponent) {
    if (allContentPanel.components.isEmpty() || allContentPanel.getComponent(0) != component) {
      allContentPanel.removeAll()
      allContentPanel.add(component)
    }
  }

  /**
   * Sets the webview component to display, if any.
   */
  @RequiresEdt
  fun setWebviewComponent(component: JComponent?) {
    webviewPanel = component
    if (component == null) {
      refreshPanelsVisibility()
    } else {
      showView(component)
    }
  }

  companion object {
    const val ONBOARDING_PANEL = "onboardingPanel"
    const val SIGN_IN_PANEL = "signInWithSourcegraphPanel"
    const val LOADING_PANEL = "loadingPanel"

    const val SIGN_IN_PANEL_INDEX = 0
    const val ONBOARDING_PANEL_INDEX = 1
    const val LOADING_PANEL_INDEX = 2

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
