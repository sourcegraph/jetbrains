package com.sourcegraph.cody.commands.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.sourcegraph.cody.commands.CommandId
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.ignore.CommandPanelIgnoreBanner
import com.sourcegraph.cody.ignore.IgnoreOracle
import com.sourcegraph.cody.ignore.IgnorePolicy
import com.sourcegraph.config.ConfigUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.plaf.ButtonUI

class CommandsTabPanel(
    private val project: Project,
    private val executeCommand: (CommandId) -> Unit
) : JBPanelWithEmptyText(GridLayout(/* rows = */ 0, /* cols = */ 1)), IgnoreOracle.FocusedFileIgnorePolicyListener {
  private val ignoreBanner = CommandPanelIgnoreBanner()
  private val buttons = mutableListOf<JComponent>()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)

    CommandId.values().forEach { command -> addCommandButton(command) }

    if (ConfigUtil.isFeatureFlagEnabled("cody.feature.inline-edits") ||
        CodyApplicationSettings.instance.isInlineEditionEnabled) {
      addInlineEditActionButton("cody.editCodeAction")
      addInlineEditActionButton("cody.documentCodeAction")
      addInlineEditActionButton("cody.testCodeAction")
    }

    addHierarchyListener {
      if (!project.isDisposed) {
        if (it.component.isShowing) {
          IgnoreOracle.getInstance(project).addListener(this)
        } else {
          IgnoreOracle.getInstance(project).removeListener(this)
        }
      }
    }
  }

  private fun addInlineEditActionButton(actionId: String) {
    val action = ActionManagerEx.getInstanceEx().getAction(actionId)
    addButton(action.templatePresentation.text, action.templatePresentation.mnemonic) {
      val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@addButton
      val dataContext = (editor as? EditorEx)?.dataContext ?: return@addButton
      val managerEx = ActionManagerEx.getInstanceEx()
      val event =
          AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), managerEx, 0)
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  }

  private fun addCommandButton(commandId: CommandId) {
    addButton(commandId.displayName, commandId.mnemonic) { executeCommand(commandId) }
  }

  private fun addButton(displayName: String, mnemonic: Int, action: () -> Unit) {
    val button = JButton(displayName)
    button.mnemonic = mnemonic
    button.displayedMnemonicIndex = displayName.indexOfFirst { it.code == mnemonic }
    button.alignmentX = Component.CENTER_ALIGNMENT
    button.maximumSize = Dimension(Int.MAX_VALUE, button.getPreferredSize().height)
    val buttonUI = DarculaButtonUI.createUI(button) as ButtonUI
    button.setUI(buttonUI)
    button.addActionListener { action() }
    add(button)

    buttons.add(button)
  }

  private fun update(policy: IgnorePolicy) {
    // Dis/enable all the buttons.
    for (button in buttons) {
      button.isEnabled = policy == IgnorePolicy.USE
    }
    when (policy) {
      IgnorePolicy.USE -> {
        remove(ignoreBanner)
      }
      IgnorePolicy.IGNORE -> {
        add(ignoreBanner, 0)
      }
    }
    revalidate()
    repaint()
  }

  override fun focusedFileIgnorePolicyChanged(policy: IgnorePolicy) {
    runInEdt {
      update(policy)
    }
  }
}
