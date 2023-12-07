package com.sourcegraph.cody

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.config.ConfigUtil

fun createSubscriptionTab(project: Project) = panel {
  row { label("<html>Current tier: <b>Cody Pro</b><html/>") }
  row {
    button("Upgrade") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/subscription") }

    if (CodyAgent.getServer(project) != null) {
      val server = CodyAgent.getServer(project)
      val evaluateFeatureFlag = server!!.evaluateFeatureFlag("cody-pro")
      if (evaluateFeatureFlag) {
        button("Check Usage") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/manage") }
      }
    }
  }
}
