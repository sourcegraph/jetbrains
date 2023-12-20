package com.sourcegraph.cody.config

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.sourcegraph.cody.CodyToolWindowFactory
import com.sourcegraph.cody.api.SourcegraphApiRequestExecutor
import com.sourcegraph.cody.api.SourcegraphApiRequests
import com.sourcegraph.cody.initialization.Activity
import com.sourcegraph.config.AccessTokenStorage
import com.sourcegraph.config.CodyApplicationService
import com.sourcegraph.config.CodyProjectService
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.config.UserLevelConfig
import java.util.concurrent.CompletableFuture

private const val RUN_ONCE_CODY_ACCOUNTS_IDS_REFRESH = "CodyAccountsIdsRefresh"
private const val RUN_ONCE_TOGGLE_CODY_TOOL_WINDOW_AFTER_MIGRATION =
    "ToggleCodyToolWindowAfterMigration"
private const val RUN_ONCE_CODY_APPLICATION_SETTINGS_MIGRATION = "CodyApplicationSettingsMigration"
private const val RUN_ONCE_CODY_PROJECT_SETTINGS_MIGRATION = "CodyProjectSettingsMigration"

class SettingsMigration : Activity {

  private val codyAuthenticationManager = CodyAuthenticationManager.instance

  override fun runActivity(project: Project) {
    RunOnceUtil.runOnceForProject(project, RUN_ONCE_CODY_PROJECT_SETTINGS_MIGRATION) {
      val customRequestHeaders = extractCustomRequestHeaders(project)
      migrateProjectSettings(project)
      migrateAccounts(project, customRequestHeaders)
    }
    RunOnceUtil.runOnceForApp(RUN_ONCE_CODY_APPLICATION_SETTINGS_MIGRATION) {
      migrateApplicationSettings()
    }
    RunOnceUtil.runOnceForApp(RUN_ONCE_TOGGLE_CODY_TOOL_WINDOW_AFTER_MIGRATION) {
      ApplicationManager.getApplication().invokeLater { toggleCodyToolbarWindow(project) }
    }
    RunOnceUtil.runOnceForApp(RUN_ONCE_CODY_ACCOUNTS_IDS_REFRESH) {
      val customRequestHeaders = extractCustomRequestHeaders(project)
      refreshAccountsIds(customRequestHeaders)
    }
  }

  private fun refreshAccountsIds(customRequestHeaders: String) {
    codyAuthenticationManager.getAccounts().forEach { codyAccount ->
      val server = SourcegraphServerPath.from(codyAccount.server.url, customRequestHeaders)
      val token = codyAuthenticationManager.getTokenForAccount(codyAccount)
      if (token != null) {
        loadUserDetails(
            SourcegraphApiRequestExecutor.Factory.instance,
            token,
            EmptyProgressIndicator(ModalityState.NON_MODAL),
            server) {
              codyAccount.id = it.id
              codyAuthenticationManager.updateAccountToken(codyAccount, token)
            }
      }
    }
  }

  private fun toggleCodyToolbarWindow(project: Project) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(CodyToolWindowFactory.TOOL_WINDOW_ID)
    toolWindow?.setAvailable(CodyApplicationSettings.instance.isCodyEnabled, null)
  }

  private fun migrateAccounts(project: Project, customRequestHeaders: String) {
    val requestExecutorFactory = SourcegraphApiRequestExecutor.Factory.instance
    migrateDotcomAccount(project, requestExecutorFactory, customRequestHeaders)
    migrateEnterpriseAccount(project, requestExecutorFactory, customRequestHeaders)
  }

  private fun migrateDotcomAccount(
      project: Project,
      requestExecutorFactory: SourcegraphApiRequestExecutor.Factory,
      customRequestHeaders: String
  ) {
    val dotcomAccessToken = extractDotcomAccessToken(project)
    if (!dotcomAccessToken.isNullOrEmpty()) {
      val server = SourcegraphServerPath.from(ConfigUtil.DOTCOM_URL, customRequestHeaders)
      val extractedAccountType = extractAccountType(project)
      val shouldSetAccountAsDefault =
          extractedAccountType == AccountType.DOTCOM ||
              extractedAccountType == AccountType.LOCAL_APP
      if (shouldSetAccountAsDefault) {
        addAsDefaultAccountIfUnique(
            project,
            dotcomAccessToken,
            server,
            requestExecutorFactory,
            EmptyProgressIndicator(ModalityState.NON_MODAL))
      } else {
        addAccountIfUnique(
            dotcomAccessToken,
            server,
            requestExecutorFactory,
            EmptyProgressIndicator(ModalityState.NON_MODAL))
      }
    }
  }

  private fun migrateEnterpriseAccount(
      project: Project,
      requestExecutorFactory: SourcegraphApiRequestExecutor.Factory,
      customRequestHeaders: String
  ) {
    val enterpriseAccessToken = extractEnterpriseAccessToken(project)
    if (!enterpriseAccessToken.isNullOrEmpty()) {
      val enterpriseUrl = extractEnterpriseUrl(project)
      runCatching { SourcegraphServerPath.from(enterpriseUrl, customRequestHeaders) }
          .fold({
            val shouldSetAccountAsDefault = extractAccountType(project) == AccountType.ENTERPRISE
            if (shouldSetAccountAsDefault) {
              addAsDefaultAccountIfUnique(
                  project,
                  enterpriseAccessToken,
                  it,
                  requestExecutorFactory,
                  EmptyProgressIndicator(ModalityState.NON_MODAL))
            } else {
              addAccountIfUnique(
                  enterpriseAccessToken,
                  it,
                  requestExecutorFactory,
                  EmptyProgressIndicator(ModalityState.NON_MODAL))
            }
          }) {
            LOG.warn("Unable to parse enterprise server url: '$enterpriseUrl'", it)
          }
    }
  }

  private fun addAccountIfUnique(
      accessToken: String,
      server: SourcegraphServerPath,
      requestExecutorFactory: SourcegraphApiRequestExecutor.Factory,
      progressIndicator: EmptyProgressIndicator,
  ) {
    loadUserDetails(requestExecutorFactory, accessToken, progressIndicator, server) {
      addAccount(CodyAccount.create(it.name, it.displayName, server, it.id), accessToken)
    }
  }

  private fun addAsDefaultAccountIfUnique(
      project: Project,
      accessToken: String,
      server: SourcegraphServerPath,
      requestExecutorFactory: SourcegraphApiRequestExecutor.Factory,
      progressIndicator: EmptyProgressIndicator,
  ) {
    loadUserDetails(requestExecutorFactory, accessToken, progressIndicator, server) {
      val codyAccount = CodyAccount.create(it.name, it.displayName, server, it.id)
      addAccount(codyAccount, accessToken)
      if (CodyAuthenticationManager.instance.getActiveAccount(project) == null)
          CodyAuthenticationManager.instance.setActiveAccount(project, codyAccount)
    }
  }

  private fun loadUserDetails(
      requestExecutorFactory: SourcegraphApiRequestExecutor.Factory,
      accessToken: String,
      progressIndicator: EmptyProgressIndicator,
      server: SourcegraphServerPath,
      onSuccess: (CodyAccountDetails) -> Unit
  ): CompletableFuture<Unit> =
      service<ProgressManager>()
          .submitIOTask(progressIndicator) {
            runCatching {
              SourcegraphApiRequests.CurrentUser(
                      requestExecutorFactory.create(accessToken), progressIndicator)
                  .getDetails(server)
            }
          }
          .successOnEdt(progressIndicator.modalityState) { accountDetailsResult ->
            accountDetailsResult.fold(onSuccess) {
              LOG.warn("Unable to load user details for '${server.url}' account", it)
            }
          }

  private fun addAccount(codyAccount: CodyAccount, token: String) {
    if (isAccountUnique(codyAccount)) {
      codyAuthenticationManager.updateAccountToken(codyAccount, token)
    }
  }

  private fun isAccountUnique(codyAccount: CodyAccount): Boolean {
    return codyAuthenticationManager.isAccountUnique(codyAccount.name, codyAccount.server)
  }

  private fun extractEnterpriseUrl(project: Project): String {
    // Project level
    val projectLevelUrl = project.service<CodyProjectService>().sourcegraphUrl
    if (!projectLevelUrl.isNullOrEmpty()) {
      return addSlashIfNeeded(projectLevelUrl)
    }

    // Application level
    val applicationLevelUrl = service<CodyApplicationService>().sourcegraphUrl
    if (!applicationLevelUrl.isNullOrEmpty()) {
      return addSlashIfNeeded(applicationLevelUrl)
    }

    // User level or default
    val userLevelUrl = UserLevelConfig.getSourcegraphUrl()
    return if (userLevelUrl.isNotEmpty()) addSlashIfNeeded(userLevelUrl) else ""
  }

  private fun extractCustomRequestHeaders(project: Project): String {
    // Project level
    val projectLevelCustomRequestHeaders =
        project.service<CodyProjectService>().getCustomRequestHeaders()
    if (!projectLevelCustomRequestHeaders.isNullOrEmpty()) {
      return projectLevelCustomRequestHeaders
    }

    // Application level
    val applicationLevelCustomRequestHeaders =
        service<CodyApplicationService>().getCustomRequestHeaders()
    return if (!applicationLevelCustomRequestHeaders.isNullOrEmpty()) {
      applicationLevelCustomRequestHeaders
    } else ""
  }

  private fun extractDefaultBranchName(project: Project): String {
    // Project level
    val projectLevelDefaultBranchName = project.service<CodyProjectService>().defaultBranchName
    if (!projectLevelDefaultBranchName.isNullOrEmpty()) {
      return projectLevelDefaultBranchName
    }

    // Application level
    val applicationLevelDefaultBranchName = service<CodyApplicationService>().defaultBranchName
    if (!applicationLevelDefaultBranchName.isNullOrEmpty()) {
      return applicationLevelDefaultBranchName
    }

    // User level or default
    val userLevelDefaultBranchName = UserLevelConfig.getDefaultBranchName()
    return userLevelDefaultBranchName ?: "main"
  }

  private fun extractRemoteUrlReplacements(project: Project): String {
    // Project level
    val projectLevelReplacements = project.service<CodyProjectService>().getRemoteUrlReplacements()
    if (!projectLevelReplacements.isNullOrEmpty()) {
      return projectLevelReplacements
    }

    // Application level
    val applicationLevelReplacements = service<CodyApplicationService>().getRemoteUrlReplacements()
    if (!applicationLevelReplacements.isNullOrEmpty()) {
      return applicationLevelReplacements
    }

    // User level or default
    val userLevelRemoteUrlReplacements = UserLevelConfig.getRemoteUrlReplacements()
    return userLevelRemoteUrlReplacements ?: ""
  }

  private fun extractDotcomAccessToken(project: Project): String? {
    // Project level overrides secure storage
    val projectLevelAccessToken = project.service<CodyProjectService>().getDotComAccessToken()
    if (projectLevelAccessToken != null) {
      return projectLevelAccessToken
    }

    // Get token from secure storage
    val securelyStoredAccessToken = AccessTokenStorage.getDotComAccessToken()
    if (securelyStoredAccessToken.isEmpty) {
      return null // Uer denied access to token storage
    }
    if (securelyStoredAccessToken.get().isNotEmpty()) {
      return securelyStoredAccessToken.get()
    }

    // No secure token found, so use app-level token.
    val codyApplicationService = service<CodyApplicationService>()
    val unsafeApplicationLevelAccessToken = codyApplicationService.dotComAccessToken
    return unsafeApplicationLevelAccessToken ?: ""
  }

  private fun extractEnterpriseAccessToken(project: Project): String? {
    // Project level overrides secure storage
    val unsafeProjectLevelAccessToken =
        project.service<CodyProjectService>().getEnterpriseAccessToken()
    if (unsafeProjectLevelAccessToken != null) {
      return unsafeProjectLevelAccessToken
    }

    // Get token from secure storage
    val securelyStoredAccessToken = AccessTokenStorage.getEnterpriseAccessToken()
    if (securelyStoredAccessToken.isEmpty) {
      return null // Uer denied access to token storage
    }
    if (securelyStoredAccessToken.get().isNotEmpty()) {
      return securelyStoredAccessToken.get()
    }

    // No secure token found, so use app-level token.
    val service = service<CodyApplicationService>()
    val unsafeApplicationLevelAccessToken = service.enterpriseAccessToken
    return unsafeApplicationLevelAccessToken ?: ""
  }

  private fun migrateProjectSettings(project: Project) {
    val codyProjectSettings = project.service<CodyProjectSettings>()
    codyProjectSettings.defaultBranchName = extractDefaultBranchName(project)
    codyProjectSettings.remoteUrlReplacements = extractRemoteUrlReplacements(project)
  }

  private fun addSlashIfNeeded(url: String): String {
    return if (url.endsWith("/")) url else "$url/"
  }

  private fun extractAccountType(project: Project): AccountType {
    return project.service<CodyProjectService>().getInstanceType()?.let { toAccountType(it) }
        ?: service<CodyApplicationService>().getInstanceType()?.let { toAccountType(it) }
            ?: AccountType.DOTCOM
  }

  private fun toAccountType(it: String): AccountType? {
    return runCatching { AccountType.valueOf(it) }.getOrNull()
  }

  private fun migrateApplicationSettings() {
    val codyApplicationSettings = service<CodyApplicationSettings>()
    val codyApplicationService = service<CodyApplicationService>()
    codyApplicationSettings.isCodyEnabled = codyApplicationService.isCodyEnabled
    codyApplicationSettings.isCodyAutocompleteEnabled =
        if (codyApplicationService.isCodyAutocompleteEnabled == true) true
        else codyApplicationService.areCodyCompletionsEnabled ?: false
    codyApplicationSettings.isCodyDebugEnabled = codyApplicationService.isCodyDebugEnabled ?: false
    codyApplicationSettings.isCodyVerboseDebugEnabled =
        codyApplicationService.isCodyVerboseDebugEnabled ?: false
    codyApplicationSettings.anonymousUserId = codyApplicationService.anonymousUserId
    codyApplicationSettings.isInstallEventLogged = codyApplicationService.isInstallEventLogged
  }

  companion object {
    private val LOG = logger<SettingsMigration>()
  }
}
