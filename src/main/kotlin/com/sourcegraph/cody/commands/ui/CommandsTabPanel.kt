package com.sourcegraph.cody.commands.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.sourcegraph.cody.autocomplete.CodyEditorFactoryListener
import com.sourcegraph.cody.commands.CommandId
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.plaf.ButtonUI

class CommandsTabPanel(
    private val project: Project,
    private val executeCommand: (CommandId) -> Unit
) : JBPanelWithEmptyText(GridLayout(/* rows = */ 0, /* cols = */ 1)) {

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    CommandId.values().forEach { command -> addCommandButton(command) }
    addInlineEditActionButton("cody.editCodeAction")
    addInlineEditActionButton("cody.documentCodyAction")
  }

  private fun executeCommandWithContext(commandId: CommandId) {
    FileEditorManager.getInstance(project).selectedTextEditor?.let {
      CodyEditorFactoryListener.Util.informAgentAboutEditorChange(it, hasFileChanged = false) {
        executeCommand(commandId)
      }
    }
  }

  private fun addInlineEditActionButton(actionId: String) {
    val action = ActionManagerEx.getInstanceEx().getAction(actionId)
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val dataContext = (editor as? EditorEx)?.dataContext ?: return

    addButton(action.templatePresentation.text, action.templatePresentation.mnemonic) {
      val managerEx = ActionManagerEx.getInstanceEx()
      val event =
          AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, Presentation(), managerEx, 0)
      if (!ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
        return@addButton
      }
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  }

  private fun addCommandButton(commandId: CommandId) {
    addButton(commandId.displayName, commandId.mnemonic) { executeCommandWithContext(commandId) }
  }

  private fun addButton(displayName: String, mnemonic: Int, action: () -> Unit) {
    val button = JButton(displayName)
    button.setMnemonic(mnemonic)
    button.alignmentX = Component.CENTER_ALIGNMENT
    button.maximumSize = Dimension(Int.MAX_VALUE, button.getPreferredSize().height)
    val buttonUI = DarculaButtonUI.createUI(button) as ButtonUI
    button.setUI(buttonUI)
    button.addActionListener { event -> action() }
    add(button)
  }
}
