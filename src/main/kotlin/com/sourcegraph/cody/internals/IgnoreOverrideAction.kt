package com.sourcegraph.cody.internals

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.TestingIgnoreOverridePolicy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.uast.util.isInstanceOf
import javax.swing.JComponent

data object ignoreOverrideModel {
  var enabled: Boolean = false
  var policy: String = """{
 "exclude": [
  { "repoNamePattern": "github\\.com/sourcegraph/cody" }
 ]
}"""
}

class IgnoreOverrideDialog(val project: Project) : DialogWrapper(project) {
  init {
    super.init()
    title = "Testing: Cody Ignore"
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      lateinit var overrideCheckbox: Cell<JBCheckBox>
      row {
        overrideCheckbox =
            checkBox("Override policy for testing").bindSelected(ignoreOverrideModel::enabled)
      }
      row {
        textArea()
          .label("Policy JSON:")
          .columns(40)
          .rows(15)
          .bindText(ignoreOverrideModel::policy)
          .validation { textArea ->
            try {
              println("${ignoreOverrideModel.policy}/${textArea.text}")
              val element = Json.parseToJsonElement(textArea.text)
              if (element is JsonObject) { null } else { ValidationInfo("Policy must be a JSON object", textArea) }
            } catch (e: SerializationException) {
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
          if (ignoreOverrideModel.enabled) {
            Json.parseToJsonElement(ignoreOverrideModel.policy).jsonObject
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
