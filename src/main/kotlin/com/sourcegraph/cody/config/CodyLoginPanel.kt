package com.sourcegraph.cody.config

import com.intellij.collaboration.async.CompletableFutureUtil.completionOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Panel
import com.sourcegraph.cody.api.SourcegraphApiRequestExecutor
import com.sourcegraph.cody.auth.SsoAuthMethod
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JTextField

internal typealias UniqueLoginPredicate = (login: String, server: SourcegraphServerPath) -> Boolean

class CodyLoginPanel(
    executorFactory: SourcegraphApiRequestExecutor.Factory,
    isAccountUnique: UniqueLoginPredicate
) : Wrapper() {

  private val serverTextField = ExtendableTextField(SourcegraphServerPath.DEFAULT_HOST, 0)
  private var tokenAcquisitionError: ValidationInfo? = null

  private lateinit var currentUi: CodyCredentialsUi
  private var tokenUi = CodyTokenCredentialsUi(serverTextField, executorFactory, isAccountUnique)

  private var authUI = CodyAuthCredentialsUi(executorFactory, isAccountUnique)

  private val progressIcon = AnimatedIcon.Default.INSTANCE
  private val progressExtension = ExtendableTextComponent.Extension { progressIcon }

  var footer: Panel.() -> Unit
    get() = tokenUi.footer
    set(value) {
      tokenUi.footer = value
      applyUi(currentUi)
    }

  init {
    applyUi(tokenUi)
  }

  private fun applyUi(ui: CodyCredentialsUi) {
    currentUi = ui
    setContent(currentUi.getPanel())
    currentUi.getPreferredFocusableComponent()?.requestFocus()
    tokenAcquisitionError = null
  }

  fun getPreferredFocusableComponent(): JComponent? =
      serverTextField.takeIf { it.isEditable && it.text.isBlank() }
          ?: currentUi.getPreferredFocusableComponent()

  fun doValidateAll(): List<ValidationInfo> {
    val uiError =
        validateCustomRequestHeaders(tokenUi.customRequestHeadersField)
            ?: currentUi.getValidator().invoke()

    return listOfNotNull(uiError, tokenAcquisitionError)
  }

  private fun validateCustomRequestHeaders(field: JTextField): ValidationInfo? {
    if (field.text.isEmpty()) {
      return null
    }
    val pairs: Array<String> =
        field.text.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    if (pairs.size % 2 != 0) {
      return ValidationInfo("Must be a comma-separated list of string pairs", field)
    }
    var i = 0
    while (i < pairs.size) {
      val headerName = pairs[i].trim { it <= ' ' }
      if (!headerName.matches("[\\w-]+".toRegex())) {
        return ValidationInfo("Invalid HTTP header name: $headerName", field)
      }
      i += 2
    }
    return null
  }

  private fun setBusy(busy: Boolean) {
    serverTextField.apply {
      if (busy) addExtension(progressExtension) else removeExtension(progressExtension)
    }
    serverTextField.isEnabled = !busy

    currentUi.setBusy(busy)
  }

  fun acquireDetailsAndToken(
      progressIndicator: ProgressIndicator,
      authMethod: SsoAuthMethod
  ): CompletableFuture<Pair<CodyAccountDetails, String>> {
    setBusy(true)
    tokenAcquisitionError = null

    val server = getServer()
    val executor = currentUi.createExecutor(server)

    return service<ProgressManager>()
        .submitIOTask(progressIndicator) {
          currentUi.acquireDetailsAndToken(executor, it, authMethod)
        }
        .completionOnEdt(progressIndicator.modalityState) { setBusy(false) }
        .errorOnEdt(progressIndicator.modalityState) { setError(it) }
  }

  fun getServer(): SourcegraphServerPath =
      SourcegraphServerPath.from(
          serverTextField.text.trim().lowercase(), tokenUi.customRequestHeadersField.text.trim())

  fun setServer(path: String) {
    serverTextField.text = path.lowercase()
  }

  fun setCustomRequestHeaders(customRequestHeaders: String) {
    tokenUi.customRequestHeadersField.text = customRequestHeaders
  }

  fun setLogin(login: String?, editable: Boolean) {
    tokenUi.setFixedLogin(if (editable) null else login)
  }

  fun setToken(token: String?) = tokenUi.setToken(token.orEmpty())

  fun setError(exception: Throwable?) {
    tokenAcquisitionError = exception?.let { currentUi.handleAcquireError(it) }
  }

  fun setTokenUi() = applyUi(tokenUi)

  fun setAuthUI() = applyUi(authUI)
}
