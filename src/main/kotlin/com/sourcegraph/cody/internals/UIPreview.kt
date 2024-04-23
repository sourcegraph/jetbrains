package com.sourcegraph.cody.internals

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.TestingIgnoreOverridePolicy
import javax.swing.JComponent

class UIPreviewAction(val project: Project) : DumbAwareAction("UI Preview") {
  // com.intellij.editorNotificationProvider extension point
  // EditorNotificationProvider
  // collectNotificationData
  // ...creates an EditorNotificationPanel
  // ...can wrap multiple ones with EditorNotificationPanel.wrapPanels

  override fun actionPerformed(e: AnActionEvent) {
    val editors = EditorFactory.getInstance().allEditors
    if (editors.size === 0) {
      return
    }
    val editor = editors[0]
    HintManager.getInstance().showInformationHint(editor, "Hello, world") {
      println("hint has been dismissed")
    }
  }
}
