package com.sourcegraph.cody.context.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SoftWrapsEditorCustomization
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.border.CompoundBorder

const val MAX_REMOTE_REPOSITORY_COUNT = 10

class RemoteRepoPopupController(val project: Project) {
  private val completionProvider = RemoteRepoCompletionProvider(project)
  fun createPopup(width: Int): JBPopup {
    val initialValue = "" // TODO: Parse initial value from repo list
    val textField = TextFieldWithAutoCompletion(project, completionProvider, false, initialValue).apply {
      border = CompoundBorder(JBUI.Borders.empty(2), border)
      addSettingsProvider { editor: EditorEx? ->
        SoftWrapsEditorCustomization.ENABLED.customize(
          editor!!
        )
      }
    }
    val panel = JPanel(BorderLayout()).apply {
      add(textField, BorderLayout.CENTER)
    }
    val shortcut = KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.shortcuts)
    val scaledHeight = JBDimension(0, 100).height
    val popup = (JBPopupFactory.getInstance().createComponentPopupBuilder(panel, textField).apply {
      setAdText("Select up to $MAX_REMOTE_REPOSITORY_COUNT repositories, use $shortcut to finish")
      setCancelOnClickOutside(true)
      setMayBeParent(true)
      setMinSize(Dimension(width, scaledHeight))
      setRequestFocus(true)
      setResizable(true)
    }).createPopup()

    // TODO: The popup "ad text" is gratuitously wrapped.

    val okAction = object : DumbAwareAction() {
      override fun actionPerformed(event: AnActionEvent) {
        unregisterCustomShortcutSet(popup.content)
        popup.closeOk(event.inputEvent)
      }
    }
    okAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, popup.content)

    // TODO: Wire up completion provider.

    return popup
  }
}