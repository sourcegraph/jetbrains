package com.sourcegraph.cody.context

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.EnhancedContextContextT
import com.sourcegraph.cody.agent.protocol.Repo
import com.sourcegraph.cody.context.ui.MAX_REMOTE_REPOSITORY_COUNT
import com.sourcegraph.cody.context.ui.RemoteRepoResolutionFailedNotification
import com.sourcegraph.cody.history.state.EnhancedContextState
import com.sourcegraph.cody.history.state.RemoteRepositoryState
import com.sourcegraph.vcs.CodebaseName
import java.util.concurrent.TimeUnit

class RemoteRepoContextModel {
  // What the user actually wrote
  var rawSpec: String = ""

  // What the user specified--strings split and de-duped
  var specified: Set<String> = emptySet()

  // What we found in the remote
  var resolved: List<Repo> = emptyList()

  // What the TypeScript extension is using for context
  var configured: List<RemoteRepo> = emptyList()
}

interface ChatContextStateProvider {
  fun getSavedState(): EnhancedContextState?
  fun updateSavedState(updater: (EnhancedContextState) -> Unit)
  fun updateAgentState(repos: List<Repo>)
  fun updateUI(repos: List<RemoteRepo>)
}

class RemoteRepoContextController(val project: Project, val chat: ChatContextStateProvider) {
  private var model = RemoteRepoContextModel()

  val rawSpec: String
    get(): String = model.rawSpec

  fun loadFromChatState(remoteRepositories: List<RemoteRepositoryState>?) {
    val cleanedRepos = remoteRepositories?.filter { it.codebaseName != null }?.toSet()?.toList()
      ?: emptyList()
    model.rawSpec = cleanedRepos.map { it.codebaseName }.joinToString("\n")
    model.specified = model.rawSpec
      .split(Regex("""\s+"""))
      .filter { it != "" }
      .toSet()

    // Update the Agent-side state for this chat.
    val enabledRepos = cleanedRepos.filter { it.isEnabled }.mapNotNull { it.codebaseName }
    RemoteRepoUtils.resolveReposWithErrorNotification(
      project, enabledRepos.map { CodebaseName(it) }, chat::updateAgentState)

    // TODO: Update the UI
  }

  fun updateRawSpec(newSpec: String) {
    // TODO: Assert not EDT
    model.rawSpec = newSpec
    model.specified = newSpec
      .split(Regex("""\s+"""))
      .filter { it != "" }
      .toSet()

    applyRepoSpec(model.specified)
  }

  fun updateFromAgent(enhancedContextStatus: EnhancedContextContextT) {
    val repos = mutableListOf<RemoteRepo>()

    for (group in enhancedContextStatus.groups) {
      val provider = group.providers.firstOrNull() ?: continue
      val name = group.displayName
      val enabled = provider.state == "ready"
      val ignored = provider.isIgnored == true
      val inclusion =
        when (provider.inclusion) {
          "auto" -> RepoInclusion.AUTO
          "manual" -> RepoInclusion.MANUAL
          else -> null
        }
      repos.add(RemoteRepo(name, isEnabled = enabled, isIgnored = ignored, inclusion = inclusion))
    }

    // TODO: Add repos which did not resolve.

    runInEdt {
      chat.updateUI(repos)
    }
  }

  // Given a textual list of repos, extract a best effort list of repositories from it and update
  // context settings.
  private fun applyRepoSpec(specified: Set<String>) {
    // TODO: Limit to 10 somewhere.

    RemoteRepoUtils.resolveReposWithErrorNotification(
      project, specified.map { CodebaseName(it) }.toList()) { trimmedRepos ->
      runInEdt {
        // Update the plugin's copy of the state.
        chat.updateSavedState { state ->
          state.remoteRepositories.clear()
          state.remoteRepositories.addAll(
            trimmedRepos.mapIndexed { index, repo ->
              RemoteRepositoryState().apply {
                codebaseName = repo.name
                isEnabled = index < MAX_REMOTE_REPOSITORY_COUNT
              }
            })
        }

        // Update the Agent state. This triggers the tree view update.
        chat.updateAgentState(trimmedRepos.take(MAX_REMOTE_REPOSITORY_COUNT))
      }
    }
  }

  fun setRepoEnabledInContextState(repoName: String, enabled: Boolean) {
    var enabledRepos = listOf<CodebaseName>()

    chat.updateSavedState { contextState ->
      contextState.remoteRepositories.find { it.codebaseName == repoName }?.isEnabled = enabled
      enabledRepos =
        contextState.remoteRepositories
          .filter { it.isEnabled }
          .mapNotNull { it.codebaseName }
          .map { CodebaseName(it) }
    }

    RemoteRepoUtils.getRepositories(project, enabledRepos)
      .completeOnTimeout(null, 15, TimeUnit.SECONDS)
      .thenApply { repos ->
        if (repos == null) {
          runInEdt { RemoteRepoResolutionFailedNotification().notify(project) }
          return@thenApply
        }
        chat.updateAgentState(repos)
      }
  }
}