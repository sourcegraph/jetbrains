package com.sourcegraph.cody.config

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.awt.RelativePoint
import com.sourcegraph.cody.auth.ui.AccountsListModel
import com.sourcegraph.cody.auth.ui.AccountsListModelBase
import com.sourcegraph.cody.telemetry.TelemetryV2
import javax.swing.JComponent

class CodyAccountListModel(private val project: Project) :
    AccountsListModelBase<CodyAccount, String>(),
    AccountsListModel.WithActive<CodyAccount, String>,
    CodyAccountsHost {

  private val actionManager = ActionManager.getInstance()

  override var activeAccount: CodyAccount? = null

  override fun addAccount(parentComponent: JComponent, point: RelativePoint?) {
    val group = actionManager.getAction("Cody.Accounts.AddAccount") as ActionGroup
    val popup = actionManager.createActionPopupMenu("LogInToSourcegraphAction", group)

    val actualPoint = point ?: RelativePoint.getCenterOf(parentComponent)
    popup.setTargetComponent(parentComponent)
    JBPopupMenu.showAt(actualPoint, popup.component)
  }

  override fun editAccount(parentComponent: JComponent, account: CodyAccount) {
    val token = newCredentials[account] ?: getOldToken(account)
    val authData =
        CodyAuthenticationManager.getInstance()
            .login(
                project,
                parentComponent,
                CodyLoginRequest(
                    title = "Edit Sourcegraph Account",
                    server = account.server,
                    login = account.name,
                    token = token,
                    customRequestHeaders = account.server.customRequestHeaders,
                    loginButtonText = "Save account",
                ))

    if (authData == null) return

    account.name = authData.login
    account.server =
        SourcegraphServerPath(authData.server.url, authData.server.customRequestHeaders)
    newCredentials[account] = authData.token
    notifyCredentialsChanged(account)
  }

  private fun getOldToken(account: CodyAccount) =
      CodyAuthenticationManager.getInstance().getTokenForAccount(account)

  override fun addAccount(
      server: SourcegraphServerPath,
      login: String,
      displayName: String?,
      token: String,
      id: String
  ) {
    TelemetryV2.sendTelemetryEvent(
        project,
        "auth.signin.token",
        "clicked",
        TelemetryEventParameters(
            billingMetadata = BillingMetadata(BillingProduct.CODY, BillingCategory.BILLABLE)))

    val account = CodyAccount(login, displayName, server, id)
    if (accountsListModel.isEmpty) {
      activeAccount = account
    }
    if (!accountsListModel.toList().contains(account)) {
      accountsListModel.add(account)
    }
    newCredentials[account] = token
    notifyCredentialsChanged(account)
  }
}
