package com.sourcegraph.cody.internals

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.selected
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.IgnorePolicySpec
import javax.swing.JComponent

data object IgnoreOverrideModel {
  var enabled: Boolean = false
  var policy: String =
      """{
 "exclude": [
  { "repoNamePattern": "github\\.com/sourcegraph/cody" }
 ]
}"""
}

class IgnoreOverrideDialog(val project: Project) : DialogWrapper(project) {
  init {
    super.init()
    title = "Testing: Cody Context Filters"
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      lateinit var overrideCheckbox: Cell<JBCheckBox>
      row {
        overrideCheckbox =
            checkBox("Override policy for testing").bindSelected(IgnoreOverrideModel::enabled)
      }
      row {
        textArea()
            .label("Policy JSON:")
            .columns(40)
            .rows(15)
            .bindText(IgnoreOverrideModel::policy)
            .validationInfo { textArea ->
              try {
                Gson().fromJson(textArea.text, IgnorePolicySpec::class.java)
                null
              } catch (e: JsonSyntaxException) {
                ValidationInfo("JSON error: ${e.message}", textArea)
              }
            }
            .enabledIf(overrideCheckbox.selected)
      }
    }
  }

  override fun doOKAction() {
    CodyAgentService.withAgent(project) { agent ->
      agent.server.testingIgnoreOverridePolicy(
          if (IgnoreOverrideModel.enabled) {
            Gson().fromJson(IgnoreOverrideModel.policy, IgnorePolicySpec::class.java)
          } else {
            null
          })
    }
    super.doOKAction()
  }
}

class IgnoreOverrideAction(val project: Project) : DumbAwareAction("Testing: Cody Ignore") {
  override fun actionPerformed(e: AnActionEvent) {
    IgnoreOverrideDialog(project).show()
  }
}
