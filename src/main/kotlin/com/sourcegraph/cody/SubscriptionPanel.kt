package com.sourcegraph.cody

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.config.ConfigUtil
import java.awt.GridLayout
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class SubscriptionTabPanel : JPanel() {

  private var isCurrentUserPro: Boolean? = null

  init {
    layout = GridLayout()
    border = EmptyBorder(JBUI.insets(4))
    add(createCenterPanel())
  }

  // todo: move strings to the bundle
  private fun createCenterPanel() = panel {
    val getIsCurrentUserPro = isCurrentUserPro
    val tier =
        if (getIsCurrentUserPro == null) "Loading..."
        else if (getIsCurrentUserPro) "Cody Pro" else "Cody Free"
    row { label("<html>Current tier: <b>$tier</b><html/>") }
    row {
      if (getIsCurrentUserPro != null && !getIsCurrentUserPro) {
        val upgradeButton =
            button("Upgrade") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/subscription") }
        upgradeButton.component.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
      }
      button("Check Usage") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/manage") }
    }
    if (getIsCurrentUserPro != null && !getIsCurrentUserPro) {
      row { text(CodyBundle.getString("tab.subscription.already-pro")) }
    }
  }

  fun update(isCurrentUserPro: Boolean?) {
    this.isCurrentUserPro = isCurrentUserPro
    this.removeAll()
    this.add(createCenterPanel())
    revalidate()
    repaint()
  }
}
