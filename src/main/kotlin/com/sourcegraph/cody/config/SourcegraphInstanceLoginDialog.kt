package com.sourcegraph.cody.config

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.async.CompletableFutureUtil.errorOnEdt
import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.setEmptyState
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.not
import com.intellij.ui.layout.selected
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.util.ui.UIUtil.getInactiveTextColor
import com.sourcegraph.cody.api.SourcegraphApiRequestExecutor
import com.sourcegraph.cody.api.SourcegraphAuthenticationException
import com.sourcegraph.cody.config.DialogValidationUtils.notBlank
import java.awt.Component
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import javax.swing.Action
import javax.swing.JCheckBox

class SourcegraphInstanceLoginDialog(project: Project?, parent: Component?) :
    DialogWrapper(project, parent, false, IdeModalityType.PROJECT) {

  private var tokenAcquisitionError: ValidationInfo? = null
  private lateinit var instanceUrlField: JBTextField
  private lateinit var tokenField: JBTextField
  private lateinit var customRequestHeadersField: ExtendableTextField
  internal lateinit var codyAuthData: CodyAuthData

  private val isAcquiringToken = JCheckBox().also { it.isSelected = false }
  private lateinit var advancedSettings: ComboBox<String>

  init {
    title = "Add Sourcegraph Account"
    setOKButtonText("Authorize in browser")
    init()
  }

  override fun createCenterPanel() = panel {
    row {
          cell(
              JBLabel("Logging in, check your browser").apply {
                icon = AnimatedIcon.Default.INSTANCE
                foreground = getInactiveTextColor()
              })
        }
        .visibleIf(isAcquiringToken.selected)
    row("Instance URL:") {
          textField()
              .applyToComponent {
                emptyText.text = "https://sourcegraph.yourcompany.com"
                text = "sourcegraph.com" // todo: remove me
                instanceUrlField = this
              }
              .horizontalAlign(HorizontalAlign.FILL)
        }
        .rowComment(
            "Enter the address of your sourcegraph instance. For example https://sourcegraph.example.org",
            maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
        .visibleIf(isAcquiringToken.selected.not())
    row("Advanced settings:") {
      comboBox(listOf("Browser", "Token")).applyToComponent {
        advancedSettings = this
        advancedSettings.addActionListener {
          if (advancedSettings.selectedItem == "Token") {
            setOKButtonText("Add Account")
          } else {
            setOKButtonText("Authorize in browser")
          }
        }
      }
    }
    row("Token:") {
          textField().applyToComponent { tokenField = this }.horizontalAlign(HorizontalAlign.FILL)
        }
        .enabledIf(advancedSettings.selectedValueMatches("Token"::equals))
    group("Advanced settings", indent = false) {
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
                .applyToComponent { setEmptyState("Client-ID, client-one, X-Extra, some metadata") }
          }
        }
        .enabledIf(advancedSettings.selectedValueMatches("Token"::equals))
  }

  override fun createActions(): Array<Action> = arrayOf(cancelAction, okAction)

  override fun doOKAction() {
    isAcquiringToken.isSelected = true
    okAction.isEnabled = false

    val emptyProgressIndicator = EmptyProgressIndicator(ModalityState.defaultModalityState())
    Disposer.register(disposable) { emptyProgressIndicator.cancel() }
    val server = deriveServerPath()

    acquireDetailsAndToken(emptyProgressIndicator)
        .successOnEdt(ModalityState.NON_MODAL) { (details, token) ->
          codyAuthData =
              CodyAuthData(
                  CodyAccount(details.username, details.displayName, server, details.id),
                  details.username,
                  token)
          close(OK_EXIT_CODE, true)
        }
        .errorOnEdt(ModalityState.NON_MODAL) {
          isAcquiringToken.isSelected = false
          okAction.isEnabled = true
          if (!CompletableFutureUtil.isCancellation(it)) startTrackingValidation()
        }
  }

  override fun doValidateAll(): MutableList<ValidationInfo> =
      listOfNotNull(
              notBlank(instanceUrlField, "Instance URL cannot be empty") ?: validateServerPath(),
              tokenAcquisitionError)
          .toMutableList()

  private fun validateServerPath(): ValidationInfo? =
      if (!isInstanceUrlValid(instanceUrlField)) {
        ValidationInfo("Invalid instance URL", instanceUrlField)
      } else {
        null
      }

  private fun isInstanceUrlValid(textField: JBTextField): Boolean =
      runCatching { SourcegraphServerPath.from(textField.text, "") }.getOrNull() != null

  private fun acquireDetailsAndToken(
      progressIndicator: ProgressIndicator
  ): CompletableFuture<Pair<CodyAccountDetails, String>> {
    tokenAcquisitionError = null

    val server = deriveServerPath()

    return service<ProgressManager>()
        .submitIOTask(progressIndicator) {
          val token = acquireToken(indicator = it, server.url)
          val executor = SourcegraphApiRequestExecutor.Factory.instance.create(server, token)
          val details =
              CodyTokenCredentialsUi.acquireDetails(executor, indicator = it, fixedLogin = null)
          return@submitIOTask details to token
        }
        .errorOnEdt(progressIndicator.modalityState) {
          tokenAcquisitionError =
              when (it) {
                is SourcegraphParseException ->
                    ValidationInfo(it.message ?: "Invalid instance URL", instanceUrlField)
                is UnknownHostException -> ValidationInfo("Server is unreachable").withOKEnabled()
                is SourcegraphAuthenticationException ->
                    ValidationInfo("Incorrect credentials.\n" + it.message.orEmpty())
                else -> ValidationInfo("Invalid authentication data.\n" + it.message.orEmpty())
              }
        }
  }

  private fun acquireToken(indicator: ProgressIndicator, server: String): String {
    //    val credentialsFuture = SourcegraphAuthService.instance.authorize(server,
    // SsoAuthMethod.DEFAULT) // todo: revert me
    val credentialsFuture =
        CompletableFuture.supplyAsync {
          for (i in 1..10) {
            Thread.sleep(1000)
            println("Logging at second: $i")
          }
          "sgp_a0d7ccb4f752ea73_d1600a23f05d74d6338bcbc9cb85fd4d49669d17"
        }
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(credentialsFuture, indicator)
    } catch (pce: ProcessCanceledException) {
      credentialsFuture.completeExceptionally(pce)
      throw pce
    }
  }

  private fun deriveServerPath(): SourcegraphServerPath =
      SourcegraphServerPath.from(
          uri = instanceUrlField.text.trim().lowercase(), customRequestHeaders = "")
}
