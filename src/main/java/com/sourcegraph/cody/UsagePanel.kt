package com.sourcegraph.cody

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.config.ConfigUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class UsagePanel(currentTierLabel: JLabel, upgradeButton: JButton, checkUsageButton: JButton, project: Project) : JPanel(GridBagLayout()) {
  init {
    add(currentTierLabel, GridBagConstraints().apply {
      gridx = 0
      gridy = 0
      anchor = GridBagConstraints.FIRST_LINE_START
    })
    add(upgradeButton, GridBagConstraints().apply {
      gridx = 0
      gridy = 1
      anchor = GridBagConstraints.LINE_START
    })
    if(CodyAgent.getServer(project) != null) {
      val server = CodyAgent.getServer(project)
      val evaluateFeatureFlag = server!!.evaluateFeatureFlag("cody-pro")
      if (evaluateFeatureFlag) {
        add(checkUsageButton, GridBagConstraints().apply {
          gridx = 1
          gridy = 1
          anchor = GridBagConstraints.CENTER
        })
      }
    }

    upgradeButton.addActionListener {
        BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/subscription")
      }
  }
}
