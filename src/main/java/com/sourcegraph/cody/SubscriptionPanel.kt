package com.sourcegraph.cody

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.ui.dsl.builder.panel
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.UpgradeToCodyProNotification
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.config.ThemeUtil

fun createSubscriptionTab(isCurrentUserPro: Boolean) = panel {
  val chatLimitError = UpgradeToCodyProNotification.chatRateLimitError.get()
  val autocompleteLimitError = UpgradeToCodyProNotification.autocompleteRateLimitError.get()
  if (!isCurrentUserPro && (chatLimitError != null || autocompleteLimitError != null)) {
    row {
      text(
          "<table width=\"100%\">" +
              "<tr>" +
              "<td width=\"10%\"><span style=\"font-size:20px;\">⚡</span></td>" +
              "<td width=\"90%\"><p>${
                if (autocompleteLimitError != null && chatLimitError != null) {
                    CodyBundle.getString("subscription-tab.chat-and-autocomplete-rate-limit-error")
                } else {
                  if (chatLimitError != null) {
                    CodyBundle.getString("subscription-tab.chat-rate-limit-error")
                  } else {
                    CodyBundle.getString("subscription-tab.autocomplete-rate-limit-error")
                  }
                }
                }</p></td>" +
              "</tr>" +
              "</table>")
    }
    if (ApplicationInfo.getInstance().getBuild().baselineVersion <= 223) {
      separator()
    } else {
      row {
        if (ThemeUtil.isDarkTheme()) {
          text("<div style=\"height: 1px; background-color: #404245;\"></div>")
        } else {
          text("<div style=\"height: 1px; background-color: #ECEDF1;\"></div>")
        }
      }
    }
  }

  val tier = if (isCurrentUserPro) "Cody Pro" else "Cody Free"
  row { label("<html>Current tier: <b>$tier</b></html>") }
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
