package com.sourcegraph.cody

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.sourcegraph.common.UpgradeToCodyProNotification
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.config.ConfigUtil

fun createSubscriptionTab(isCurrentUserPro: Boolean) = panel {
  val chatLimitError = UpgradeToCodyProNotification.chatRateLimitError.get()
  val autocompleteLimitError = UpgradeToCodyProNotification.autocompleteRateLimitError.get()
  if (!isCurrentUserPro) {
    if (chatLimitError != null && autocompleteLimitError != null) {
      row {
        label(
            "<html>\u26A1 You used all you autocompletions and chats for this month. Upgrade to Cody Pro to get unlimited interactions.</html>")
      }
      separator().topGap(TopGap.SMALL).bottomGap(BottomGap.SMALL)
    } else if (chatLimitError != null) {
      row {
        label(
            "<html>\u26A1 You used all ${chatLimitError.limit} chat messages for this month. Upgrade to Cody Pro to get unlimited chats.</html>")
      }
      separator().topGap(TopGap.SMALL).bottomGap(BottomGap.SMALL)
    } else if (autocompleteLimitError != null) {
      row {
        label(
            "<html>\u26A1 You used all ${autocompleteLimitError.limit} autocompletions for this month. Upgrade to Cody Pro to get unlimited autocompletions.</html>")
        cell(SeparatorComponent(1, 1))
      }
      separator().topGap(TopGap.SMALL).bottomGap(BottomGap.SMALL)
    }
  }
  val tier = if (isCurrentUserPro) "Cody Pro" else "Cody Free"
  row { label("<html>Current tier: <b>$tier</b><html/>") }
  row {
    if (!isCurrentUserPro) {
      val upgradeButton =
          button("Upgrade") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/subscription") }
      upgradeButton.component.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
    }
    button("Check Usage") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/manage") }
  }
  if (!isCurrentUserPro) {
    row { text(CodyBundle.getString("tab.subscription.already-pro")) }
  }
}
