package com.sourcegraph.cody.chat

import com.intellij.ide.DataManager
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContextWrapper
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ColorUtil
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.auth.ui.SignInWithEnterpriseInstanceAction
import com.sourcegraph.cody.config.CodyAccountsHost
import com.sourcegraph.cody.config.CodyPersistentAccountsHost
import com.sourcegraph.cody.config.LogInToSourcegraphAction
import com.sourcegraph.cody.ui.HtmlViewer.createHtmlViewer
import com.sourcegraph.cody.ui.UnderlinedActionLink
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border

class SignInWithSourcegraphPanel(private val project: Project) : JPanel() {

  private val signInWithGithubButton =
      UIComponents.createMainButton("Sign in with GitHub", Icons.SignIn.Github)
  private val signInWithGitlabButton =
      UIComponents.createMainButton("Sign in with GitLab", Icons.SignIn.Gitlab)
  private val signInWithGoogleButton =
      UIComponents.createMainButton("Sign in with Google", Icons.SignIn.Google)

  init {
    val jEditorPane = createHtmlViewer(UIUtil.getPanelBackground())
    jEditorPane.text =
        ("<html><body><h2>Welcome to Cody</h2>" +
            "<p>Understand and write code faster with an AI assistant</p>" +
            "</body></html>")
    val signInWithGithubButton = signInWithGithubButton
    val signInWithGitlabButton = signInWithGitlabButton
    val signInWithGoogleButton = signInWithGoogleButton
    signInWithGithubButton.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    signInWithGitlabButton.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    signInWithGoogleButton.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    val logInToSourcegraphAction = LogInToSourcegraphAction()

    signInWithGithubButton.addActionListener(
        getSignInAction(signInWithGithubButton, logInToSourcegraphAction))
    signInWithGitlabButton.addActionListener(
        getSignInAction(signInWithGitlabButton, logInToSourcegraphAction))
    signInWithGoogleButton.addActionListener(
        getSignInAction(signInWithGoogleButton, logInToSourcegraphAction))

    val panelWithTheMessage = JPanel()
    panelWithTheMessage.setLayout(BoxLayout(panelWithTheMessage, BoxLayout.Y_AXIS))
    jEditorPane.setMargin(JBUI.emptyInsets())
    val paddingInsideThePanel: Border =
        JBUI.Borders.empty(ADDITIONAL_PADDING_FOR_HEADER, PADDING, 0, PADDING)
    val hiImCodyLabel = JLabel(Icons.HiImCody)
    val hiImCodyPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
    hiImCodyPanel.add(hiImCodyLabel)
    panelWithTheMessage.add(hiImCodyPanel)
    panelWithTheMessage.add(jEditorPane)
    panelWithTheMessage.setBorder(paddingInsideThePanel)
    val separatorPanel = JPanel(BorderLayout())
    separatorPanel.setBorder(JBUI.Borders.empty(PADDING, 0))
    val separatorComponent =
        SeparatorComponent(
            3, ColorUtil.brighter(UIUtil.getPanelBackground(), 3), UIUtil.getPanelBackground())
    separatorPanel.add(separatorComponent)
    panelWithTheMessage.add(separatorPanel)
    val buttonPanelGithub = JPanel(BorderLayout())
    val buttonPanelGitlab = JPanel(BorderLayout())
    val buttonPanelGoogle = JPanel(BorderLayout())
    buttonPanelGithub.add(signInWithGithubButton, BorderLayout.CENTER)
    buttonPanelGitlab.add(signInWithGitlabButton, BorderLayout.CENTER)
    buttonPanelGoogle.add(signInWithGoogleButton, BorderLayout.CENTER)
    panelWithTheMessage.add(buttonPanelGithub)
    panelWithTheMessage.add(buttonPanelGitlab)
    panelWithTheMessage.add(buttonPanelGoogle)
    setLayout(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false))
    setBorder(JBUI.Borders.empty(PADDING))
    this.add(panelWithTheMessage)
    this.add(createPanelWithSignInWithAnEnterpriseInstance())
  }

  private fun getSignInAction(
      signInWithGithubButton: JButton,
      logInToSourcegraphAction: LogInToSourcegraphAction
  ): (e: ActionEvent) -> Unit {
    val functionGithub: (e: ActionEvent) -> Unit = {
      val dataContext = DataManager.getInstance().getDataContext(signInWithGithubButton)
      val dataContextWrapper = DataContextWrapper(dataContext)
      val accountsHost: CodyAccountsHost = CodyPersistentAccountsHost(project)
      dataContextWrapper.putUserData(CodyAccountsHost.KEY, accountsHost)
      val event =
          AnActionEvent(
              null,
              dataContext,
              ActionPlaces.POPUP,
              Presentation(),
              ActionManager.getInstance(),
              it.modifiers)
      if (ActionUtil.lastUpdateAndCheckDumb(logInToSourcegraphAction, event, false)) {
        ActionUtil.performActionDumbAwareWithCallbacks(logInToSourcegraphAction, event)
      }
    }
    return functionGithub
  }

  fun addMainButtonActionListener(actionListener: ActionListener) {
    signInWithGithubButton.addActionListener(actionListener)
  }

  private fun createPanelWithSignInWithAnEnterpriseInstance(): JPanel {
    val signInWithAnEnterpriseInstance: AnActionLink =
        UnderlinedActionLink(
            "Sign in with an Enterprise Instance", SignInWithEnterpriseInstanceAction(""))
    signInWithAnEnterpriseInstance.setAlignmentX(CENTER_ALIGNMENT)
    val panelWithSettingsLink = JPanel(BorderLayout())
    panelWithSettingsLink.setBorder(JBUI.Borders.empty(PADDING, 0))
    val linkPanel = JPanel(GridBagLayout())
    linkPanel.add(signInWithAnEnterpriseInstance)
    panelWithSettingsLink.add(linkPanel, BorderLayout.PAGE_START)
    return panelWithSettingsLink
  }

  companion object {
    private const val PADDING = 20

    // 10 here is the default padding from the styles of the h2 and we want to make the whole
    // padding
    // to be 20, that's why we need the difference between our PADDING and the default padding of
    // the
    // h2
    private const val ADDITIONAL_PADDING_FOR_HEADER = PADDING - 10
  }
}
