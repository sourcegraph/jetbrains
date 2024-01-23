package com.sourcegraph.cody.commands.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.sourcegraph.cody.autocomplete.CodyEditorFactoryListener
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.telemetry.GraphQlLogger
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.plaf.ButtonUI

class CommandsTabPanel(private val project: Project, private val executeCommand: (String) -> Unit) :
    JBPanelWithEmptyText(GridLayout(/* rows = */ 0, /* cols = */ 1)) {

  private val commands: Map<String, String> =
      mapOf(
          "commands/explain" to "Explain Code",
          "commands/smell" to "Smell Code",
          "commands/test" to "Generate Test")

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    commands.forEach { (command, text) -> addButton(command, text) }
    CommandsContextMenu.addCommandsToCodyContextMenu(project, commands) {
      executeCommandWithContext(it)
    }
  }

  fun enableAllButtons() = switchAllButtons(isEnabled = true, tooltip = null)

  fun disableAllButtons() =
      switchAllButtons(
          isEnabled = false, tooltip = CodyBundle.getString("commands-tab.message-in-progress"))

  private fun switchAllButtons(isEnabled: Boolean, tooltip: String?) {
    components.filterIsInstance<JButton>().forEach {
      it.isEnabled = isEnabled
      it.toolTipText = tooltip
    }
  }

  private fun executeCommandWithContext(command: String) {
    ApplicationManager.getApplication().executeOnPooledThread {
      GraphQlLogger.logCodyEvent(project, "command:$command", "clicked")
    }

    FileEditorManager.getInstance(project).selectedTextEditor?.let {
      CodyEditorFactoryListener.Util.informAgentAboutEditorChange(it, hasFileChanged = false) {
        executeCommand(command)
      }
    }
  }

  private fun addButton(command: String, text: String) {
    val button = JButton(text)
    button.alignmentX = Component.CENTER_ALIGNMENT
    button.maximumSize = Dimension(Int.MAX_VALUE, button.getPreferredSize().height)
    val buttonUI = DarculaButtonUI.createUI(button) as ButtonUI
    button.setUI(buttonUI)
    button.addActionListener { executeCommandWithContext(command) }
    add(button)
  }
}
