package com.sourcegraph.cody.config

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.setEmptyState
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.enteredTextSatisfies
import com.sourcegraph.cody.api.SourcegraphApiRequestExecutor
import com.sourcegraph.cody.api.SourcegraphApiRequests
import com.sourcegraph.cody.api.SourcegraphAuthenticationException
import com.sourcegraph.cody.auth.SsoAuthMethod
import com.sourcegraph.cody.config.DialogValidationUtils.custom
import com.sourcegraph.cody.config.DialogValidationUtils.notBlank
import com.sourcegraph.common.AuthorizationUtil
import com.sourcegraph.common.BrowserOpener
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.swing.JComponent
import javax.swing.JTextField

internal class CodyTokenCredentialsUi(
    private val serverTextField: ExtendableTextField,
    val factory: SourcegraphApiRequestExecutor.Factory
) : CodyCredentialsUi() {

  lateinit var customRequestHeadersField: ExtendableTextField
  private val tokenTextField = JBTextField()
  private var fixedLogin: String? = null

  fun setToken(token: String) {
    tokenTextField.text = token
  }

  override fun Panel.centerPanel() {
    lateinit var serverField: Cell<ExtendableTextField>
    row("Server: ") { serverField = cell(serverTextField).horizontalAlign(HorizontalAlign.FILL) }
    row("Token: ") { cell(tokenTextField).horizontalAlign(HorizontalAlign.FILL) }
    row("") {
      link("Generate token manually") { BrowserOpener.openInBrowser(null, buildNewTokenUrl()) }
          .enabledIf(
              serverField.component.enteredTextSatisfies {
                it.isNotEmpty() && isServerPathValid(it)
              })
    }
    collapsibleGroup("Advanced settings", indent = false) {
      row("Custom request headers: ") {
        customRequestHeadersField = ExtendableTextField("", 0)
        cell(customRequestHeadersField)
            .horizontalAlign(HorizontalAlign.FILL)
            .comment(
                """Any custom headers to send with every request to Sourcegraph.<br>
                  |Use any number of pairs: "header1, value1, header2, value2, ...".<br>
                  |Whitespace around commas doesn't matter.
              """
                    .trimMargin(),
                MAX_LINE_LENGTH_NO_WRAP)
            .applyToComponent {
              this.setEmptyState("Client-ID, client-one, X-Extra, some metadata")
            }
      }
    }
  }

  override fun getPreferredFocusableComponent(): JComponent = tokenTextField

  private fun buildNewTokenUrl(): String {
    val sourcegraphServerPath =
        runCatching { SourcegraphServerPath.from(serverTextField.text, "") }.getOrNull()
            ?: return ""
    val productName = ApplicationNamesInfo.getInstance().fullProductName
    val productNameEncoded = URLEncoder.encode(productName, "UTF-8")
    return sourcegraphServerPath.url + "user/settings/tokens/new?description=" + productNameEncoded
  }

  override fun getValidationInfo() =
      getServerPathValidationInfo()
          ?: notBlank(tokenTextField, "Token cannot be empty")
          ?: custom(tokenTextField, "Invalid access token") {
            AuthorizationUtil.isValidAccessToken(tokenTextField.text)
          }

  fun getServerPathValidationInfo(): ValidationInfo? {
    return notBlank(serverTextField, "Server url cannot be empty")
        ?: validateServerPath(serverTextField)
  }

  private fun validateServerPath(field: JTextField): ValidationInfo? =
      if (!isServerPathValid(field.text)) {
        ValidationInfo("Invalid server url", field)
      } else {
        null
      }

  private fun isServerPathValid(text: String): Boolean {
    return runCatching { SourcegraphServerPath.from(text, "") }.getOrNull() != null
  }

  override fun createExecutor(server: SourcegraphServerPath): SourcegraphApiRequestExecutor {
    return factory.create(server, tokenTextField.text)
  }

  override fun acquireDetailsAndToken(
      executor: SourcegraphApiRequestExecutor,
      indicator: ProgressIndicator,
      authMethod: SsoAuthMethod
  ): Pair<CodyAccountDetails, String> {
    val details = acquireDetails(executor, indicator, fixedLogin)
    return details to tokenTextField.text
  }

  override fun handleAcquireError(error: Throwable): ValidationInfo =
      when (error) {
        is SourcegraphParseException ->
            ValidationInfo(error.message ?: "Invalid server url", serverTextField)
        else -> handleError(error)
      }

  override fun setBusy(busy: Boolean) {
    tokenTextField.isEnabled = !busy
  }

  fun setFixedLogin(fixedLogin: String?) {
    this.fixedLogin = fixedLogin
  }

  companion object {

    fun acquireDetails(
        executor: SourcegraphApiRequestExecutor,
        indicator: ProgressIndicator,
        fixedLogin: String?
    ): CodyAccountDetails {
      val accountDetails = SourcegraphApiRequests.CurrentUser(executor, indicator).getDetails()

      val login = accountDetails.username
      if (fixedLogin != null && fixedLogin != login)
          throw SourcegraphAuthenticationException("Token should match username \"$fixedLogin\".")

      return accountDetails
    }

    fun handleError(error: Throwable): ValidationInfo =
        when (error) {
          is UnknownHostException -> ValidationInfo("Server is unreachable.").withOKEnabled()
          is SourcegraphAuthenticationException ->
              ValidationInfo("Incorrect credentials.\n" + error.message.orEmpty()).withOKEnabled()
          else ->
              ValidationInfo("Invalid authentication data.\n" + error.message.orEmpty())
                  .withOKEnabled()
        }
  }
}
