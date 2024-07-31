package com.sourcegraph.cody.config

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.api.SourcegraphApiRequestExecutor
import com.sourcegraph.cody.auth.SsoAuthMethod
import javax.swing.JComponent
import javax.swing.JPanel

abstract class CodyCredentialsUi {
  abstract fun getPreferredFocusableComponent(): JComponent?

  abstract fun getValidationInfo(): ValidationInfo?

  abstract fun createExecutor(server: SourcegraphServerPath): SourcegraphApiRequestExecutor

  abstract fun acquireDetailsAndToken(
      executor: SourcegraphApiRequestExecutor,
      indicator: ProgressIndicator,
      authMethod: SsoAuthMethod
  ): Pair<CodyAccountDetails, String>

  abstract fun handleAcquireError(error: Throwable): ValidationInfo

  abstract fun setBusy(busy: Boolean)

  var footer: Panel.() -> Unit = {}

  fun getPanel(): JPanel =
      panel {
            centerPanel()
            footer()
          }
          .apply {
            // Border is required to have more space - otherwise there could be issues with focus
            // ring.
            // `getRegularPanelInsets()` is used to simplify border calculation for dialogs where
            // this panel is used.
            border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
          }

  protected abstract fun Panel.centerPanel()
}
