package com.sourcegraph.cody.config

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.sourcegraph.cody.api.SourcegraphApiRequestExecutor
import com.sourcegraph.cody.auth.SsoAuthMethod
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent

abstract class BaseLoginDialog(
    val project: Project?,
    val parent: Component?,
    executorFactory: SourcegraphApiRequestExecutor.Factory,
    private val authMethod: SsoAuthMethod
) : DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {

  protected val loginPanel = CodyLoginPanel(executorFactory)

  private val authenticateAutomatically =
      object : DialogWrapperAction("Authenticate automatically") {

        override fun doAction(e: ActionEvent?) {
          val info = loginPanel.getServerPathValidationInfo()
          if (info != null) {
            if (info.component != null && info.component!!.isVisible) {
              IdeFocusManager.getInstance(null).requestFocus(info.component!!, true)
            }

            updateErrorInfo(listOf(info))
            this.isEnabled = info.okEnabled
            startTrackingValidation()
            if (!info.okEnabled) {
              return
            }
          }
          authenticate()
        }
      }

  override fun createActions(): Array<Action> =
      arrayOf(cancelAction, authenticateAutomatically, okAction)

  override fun updateErrorInfo(errors: List<ValidationInfo>) {
    super.updateErrorInfo(errors)
    if (errors.none { it.component is ServerTextField }) {
      authenticateAutomatically.isEnabled = true
    }
  }

  var id: String = ""
    private set

  var login: String = ""
    private set

  var displayName: String? = null
    private set

  var token: String = ""
    private set

  val server: SourcegraphServerPath
    get() = loginPanel.getServer()

  fun setToken(token: String?) = loginPanel.setToken(token)

  fun setLogin(login: String?) = loginPanel.setLogin(login, false)

  fun setServer(path: String) = loginPanel.setServer(path)

  fun setCustomRequestHeaders(customRequestHeaders: String) =
      loginPanel.setCustomRequestHeaders(customRequestHeaders)

  fun setLoginButtonText(text: String) = setOKButtonText(text)

  fun setError(exception: Throwable) {
    loginPanel.setError(exception)
    startTrackingValidation()
  }

  override fun getPreferredFocusedComponent(): JComponent? =
      loginPanel.getPreferredFocusableComponent()

  override fun doValidateAll(): List<ValidationInfo> = loginPanel.doValidateAll()

  override fun doOKAction() {
    val modalityState = ModalityState.stateForComponent(loginPanel)
    val emptyProgressIndicator = EmptyProgressIndicator(modalityState)
    Disposer.register(disposable) { emptyProgressIndicator.cancel() }

    loginPanel
        .acquireDetailsAndToken(emptyProgressIndicator, authMethod)
        .successOnEdt(modalityState) { (details, newToken) ->
          login = details.username
          displayName = details.displayName
          token = newToken
          id = details.id

          close(OK_EXIT_CODE, true)
        }
        .errorOnEdt(modalityState) {
          if (!CompletableFutureUtil.isCancellation(it)) startTrackingValidation()
        }
  }

  fun authenticate() {
    val modalityState = ModalityState.stateForComponent(loginPanel)
    val emptyProgressIndicator = EmptyProgressIndicator(modalityState)
    Disposer.register(disposable) { emptyProgressIndicator.cancel() }

    loginPanel.setAuthUI()
    authenticateAutomatically.isEnabled = false
    okAction.isEnabled = false

    loginPanel
        .acquireDetailsAndToken(emptyProgressIndicator, SsoAuthMethod.DEFAULT)
        .successOnEdt(modalityState) { (details, newToken) ->
          login = details.username
          displayName = details.displayName
          token = newToken
          id = details.id

          close(OK_EXIT_CODE, true)
        }
        .errorOnEdt(modalityState) {
          if (!CompletableFutureUtil.isCancellation(it)) startTrackingValidation()
        }
  }
}
