package com.sourcegraph.cody.context.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.LanguageTextField
import java.awt.BorderLayout
import javax.swing.JPanel

const val MAX_REMOTE_REPOSITORY_COUNT = 10

class RemoteRepoPopupController(val project: Project) {
  fun createPopup(): JBPopup {
    val initialValue = "" // TODO: Parse initial value from repo list
    val textField = LanguageTextField(PlainTextLanguage.INSTANCE, project, initialValue, LanguageTextField.SimpleDocumentCreator())
    val panel = JPanel(BorderLayout()).apply {
      add(textField, BorderLayout.CENTER)
    }
    val shortcut = KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.shortcuts)
    val popup = (JBPopupFactory.getInstance().createComponentPopupBuilder(panel, textField).apply {
      setCancelOnClickOutside(true)
      setAdText("Select up to $MAX_REMOTE_REPOSITORY_COUNT repositories, use $shortcut to finish")
      setRequestFocus(true)
      setResizable(true)
      setMayBeParent(true)
    }).createPopup()

    // TODO: Set minimum size.

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