package com.sourcegraph.config

import com.google.gson.JsonObject
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.ExtensionConfiguration
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.config.ServerAuthLoader
import com.sourcegraph.cody.config.SourcegraphServerPath
import com.sourcegraph.cody.config.SourcegraphServerPath.Companion.from
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors
import org.jetbrains.annotations.Contract

object ConfigUtil {
  const val DOTCOM_URL = "https://sourcegraph.com/"
  const val SERVICE_DISPLAY_NAME = "Sourcegraph"
  const val CODY_DISPLAY_NAME = "Cody"
  const val CODE_SEARCH_DISPLAY_NAME = "Code Search"
  const val SOURCEGRAPH_DISPLAY_NAME = "Sourcegraph"

  @JvmStatic
  fun getAgentConfiguration(project: Project): ExtensionConfiguration {
    val serverAuth = ServerAuthLoader.loadServerAuth(project)
    val codyAgentCodebase = CodyAgent.getClient(project).codebase

    return ExtensionConfiguration(
        anonymousUserID = CodyApplicationSettings.instance.anonymousUserId,
        serverEndpoint = serverAuth.instanceUrl,
        accessToken = serverAuth.accessToken,
        customHeaders = getCustomRequestHeadersAsMap(serverAuth.customRequestHeaders),
        proxy = UserLevelConfig.getProxy(),
        autocompleteAdvancedServerEndpoint = UserLevelConfig.getAutocompleteServerEndpoint(),
        autocompleteAdvancedAccessToken = UserLevelConfig.getAutocompleteAccessToken(),
        autocompleteAdvancedProvider =
            UserLevelConfig.getAutocompleteProviderType()?.vscodeSettingString(),
        debug = isCodyDebugEnabled(),
        verboseDebug = isCodyVerboseDebugEnabled(),
        codebase = codyAgentCodebase?.getUrl(),
    )
  }

  @JvmStatic
  fun getConfigAsJson(project: Project): JsonObject {
    val (instanceUrl, accessToken, customRequestHeaders) = ServerAuthLoader.loadServerAuth(project)
    return JsonObject().apply {
      addProperty("instanceURL", instanceUrl)
      addProperty("accessToken", accessToken)
      addProperty("customRequestHeadersAsString", customRequestHeaders)
      addProperty("pluginVersion", getPluginVersion())
      addProperty("anonymousUserId", CodyApplicationSettings.instance.anonymousUserId)
    }
  }

  @JvmStatic
  fun getServerPath(project: Project): SourcegraphServerPath {
    val activeAccount = CodyAuthenticationManager.instance.getActiveAccount(project)
    return activeAccount?.server ?: from(DOTCOM_URL, "")
  }

  @JvmStatic
  fun getCustomRequestHeadersAsMap(customRequestHeaders: String): Map<String, String> {
    val result: MutableMap<String, String> = HashMap()
    val pairs =
        customRequestHeaders.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    var i = 0
    while (i + 1 < pairs.size) {
      result[pairs[i]] = pairs[i + 1]
      i += 2
    }
    return result
  }

  @JvmStatic
  @Contract(pure = true)
  fun getPluginVersion(): String {
    // Internal version
    val plugin = PluginManagerCore.getPlugin(PluginId.getId("com.sourcegraph.jetbrains"))
    return if (plugin != null) plugin.version else "unknown"
  }

  @JvmStatic fun isCodyEnabled(): Boolean = CodyApplicationSettings.instance.isCodyEnabled

  @JvmStatic fun isCodyDebugEnabled(): Boolean = CodyApplicationSettings.instance.isCodyDebugEnabled

  @JvmStatic
  fun isCodyVerboseDebugEnabled(): Boolean =
      CodyApplicationSettings.instance.isCodyVerboseDebugEnabled

  @JvmStatic
  fun isCodyAutocompleteEnabled(): Boolean =
      CodyApplicationSettings.instance.isCodyAutocompleteEnabled

  @JvmStatic
  fun isCustomAutocompleteColorEnabled(): Boolean =
      CodyApplicationSettings.instance.isCustomAutocompleteColorEnabled

  @JvmStatic
  fun getCustomAutocompleteColor(): Int? = CodyApplicationSettings.instance.customAutocompleteColor

  @JvmStatic
  fun getWorkspaceRootPath(project: Project): Path {
    return Paths.get(getWorkspaceRoot(project))
  }

  @JvmStatic
  fun getWorkspaceRoot(project: Project): String {
    // The base path should only be null for the default project. The agent server assumes that the
    // workspace root is not null, so we have to provide some default value. Feel free to change to
    // something else than the home directory if this is causing problems.
    return project.basePath ?: System.getProperty("user.home")
  }

  @JvmStatic
  fun getAllEditors(): List<Editor> {
    val openProjects = ProjectManager.getInstance().openProjects
    return Arrays.stream(openProjects)
        .flatMap { project: Project? ->
          Arrays.stream(FileEditorManager.getInstance(project!!).allEditors)
        }
        .filter { fileEditor: FileEditor? -> fileEditor is TextEditor }
        .map { fileEditor: FileEditor -> (fileEditor as TextEditor).editor }
        .collect(Collectors.toList())
  }

  @JvmStatic
  fun getBlacklistedAutocompleteLanguageIds(): List<String> {
    return CodyApplicationSettings.instance.blacklistedLanguageIds
  }

  @JvmStatic
  fun getShouldAcceptNonTrustedCertificatesAutomatically(): Boolean {
    return CodyApplicationSettings.instance.shouldAcceptNonTrustedCertificatesAutomatically
  }
}
