package com.sourcegraph.cody.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentException
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.RemoteRepoFetchState
import com.sourcegraph.cody.agent.protocol.RemoteRepoHasParams
import com.sourcegraph.cody.agent.protocol.RemoteRepoListParams
import com.sourcegraph.cody.edit.EditSession
import com.sourcegraph.cody.error.CodyError
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class RemoteRepoSearcher(private val project: Project) {
  companion object {
    fun getInstance(project: Project): RemoteRepoSearcher {
      return project.service<RemoteRepoSearcher>()
    }
  }

  private val logger = Logger.getInstance(RemoteRepoSearcher::class.java)
  private var _state: RemoteRepoFetchState = RemoteRepoFetchState(state = "paused", error = null)

  val isDoneLoading: Boolean
    @Synchronized
    get() = _state.state == "errored" || _state.state == "complete"

  val error: CodyError?
    @Synchronized
    get() = if (_state.state == "errored") { _state.error } else { null }

  /**
   * Gets whether `repoName` is a known remote repo. This may block while repo loading is in progress.
   */
  fun has(repoName: String): CompletableFuture<Boolean> {
    val result = CompletableFuture<Boolean>()
    CodyAgentService.withAgent(project) { agent ->
      try {
        result.complete(agent.server.remoteRepoHas(RemoteRepoHasParams(repoName)).get().result)
      } catch (e: Exception) {
        result.completeExceptionally(e)
      }
    }
    return result
  }

  fun search(query: String?): Channel<List<String>> {
    val result = CompletableFuture<List<String>>()
    CodyAgentService.withAgent(project) { agent ->
      try {
        val repos = agent.server.remoteRepoList(
          RemoteRepoListParams(
            query = query,
            first = 500,
            after = null,
          )
        ).get()
        if (repos.state.error != null) {
          logger.warn("remote repository search had error: ${repos.state.error.title}")
          if (repos.repos.isEmpty()) {
            result.completeExceptionally(CodyAgentException(repos.state.error.title))
          }
        }
        synchronized(this) {
          this._state = repos.state
        }
        result.complete(repos.repos.map { it.name })
      } catch (e: Exception) {
        result.completeExceptionally(e)
      }
    }
    return result
  }

  // Callbacks for CodyAgentService
  fun remoteRepoDidChange() {
    // TODO: If there's an in-progress load, then unblock it.
    println("remoteRepoDidChange")
  }

  @Synchronized
  fun remoteRepoDidChangeState(state: RemoteRepoFetchState) {
    // TODO: If there's an in-progress load, then unblock it.
    _state = state
    println("remoteRepoDidChangeState ${state.state}/${state.error}")
  }
}