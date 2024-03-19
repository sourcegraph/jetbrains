package com.sourcegraph.cody

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.UpgradeToCodyProNotification
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.config.ThemeUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class MyAccountTabPanel(val project: Project) : JPanel() {

  private var chatLimitError = UpgradeToCodyProNotification.chatRateLimitError.get()
  private var autocompleteLimitError = UpgradeToCodyProNotification.autocompleteRateLimitError.get()

  init {
    layout = BorderLayout()
    border = EmptyBorder(JBUI.insets(4))
    if (chatLimitError != null || autocompleteLimitError != null) {
      add(createRateLimitPanel(), BorderLayout.PAGE_START)
    }
    ApplicationManager.getApplication()
        .messageBus
        .connect()
        .subscribe(LafManagerListener.TOPIC, LafManagerListener { update() })
  }

  private fun createRateLimitPanel() = panel {
    row {
      text(
          "<table width=\"100%\">" +
              "<tr>" +
              "<td width=\"10%\"><span style=\"font-size:20px;\">⚡</span></td>" +
              "<td width=\"90%\"><p>${
                  if (autocompleteLimitError != null && chatLimitError != null) {
                    CodyBundle.getString("my-account-tab.chat-and-autocomplete-rate-limit-error")
                  } else {
                    if (chatLimitError != null) {
                      CodyBundle.getString("my-account-tab.chat-rate-limit-error")
                    } else {
                      CodyBundle.getString("my-account-tab.autocomplete-rate-limit-error")
                    }
                  }
                }</p></td>" +
              "</tr>" +
              "</table>")
    }

    row {
      if (ThemeUtil.isDarkTheme()) {
        text("<div style=\"height: 1px; background-color: #404245;\"></div>")
      } else {
        text("<div style=\"height: 1px; background-color: #ECEDF1;\"></div>")
      }
    }
  }

  private fun createCenterPanel(isPro: Boolean?) = panel {
    val tier =
        if (isPro == null) CodyBundle.getString("my-account-tab.loading-label")
        else if (isPro) CodyBundle.getString("my-account-tab.cody-pro-label")
        else CodyBundle.getString("my-account-tab.cody-free-label")
    row { label("<html>Current tier: <b>$tier</b><html/>") }
    row {
      if (isPro != null && !isPro) {
        val upgradeButton =
            button("Upgrade") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/subscription") }
        upgradeButton.component.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
      }
      button("Check Usage") { BrowserUtil.browse(ConfigUtil.DOTCOM_URL + "cody/manage") }
    }
    if (isPro != null && !isPro) {
      row { text(CodyBundle.getString("my-account-tab.already-pro")) }
    }
  }

  @RequiresEdt
  fun update() {
    this.removeAll()
    chatLimitError = UpgradeToCodyProNotification.chatRateLimitError.get()
    autocompleteLimitError = UpgradeToCodyProNotification.autocompleteRateLimitError.get()
    if (chatLimitError != null || autocompleteLimitError != null) {
      this.add(createRateLimitPanel(), BorderLayout.PAGE_START)
    }

    val activeAccount = CodyAuthenticationManager.instance.getActiveAccount(project)
    val isProFuture = activeAccount?.isProUser(project)
    val centerPanel = createCenterPanel(isProFuture?.getNow(null))
    this.add(centerPanel)

    isProFuture?.thenAccept { isPro ->
      invokeLater {
        this.remove(centerPanel)
        this.add(createCenterPanel(isPro))
        revalidate()
        repaint()
      }
    }

    revalidate()
    repaint()
  }
}
