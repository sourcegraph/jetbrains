package com.sourcegraph.cody.agent

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.withScheme
import com.sourcegraph.cody.chat.AgentChatSessionService
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.context.RemoteRepoSearcher
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.ignore.IgnoreOracle
import com.sourcegraph.cody.listeners.CodyFileEditorListener
import com.sourcegraph.cody.statusbar.CodyStatusService
import com.sourcegraph.utils.CodyEditorUtil
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import java.util.function.Function
import kotlinx.coroutines.runBlocking

@Service(Service.Level.PROJECT)
class CodyAgentService(project: Project) : Disposable {

  @Volatile private var codyAgent: CompletableFuture<CodyAgent> = CompletableFuture()

  private val startupActions: MutableList<(CodyAgent) -> Unit> = mutableListOf()

  init {
    onStartup { agent ->
      agent.client.onNewMessage = Consumer { params ->
        if (!project.isDisposed) {
          AgentChatSessionService.getInstance(project)
              .getSession(params.id)
              ?.receiveMessage(params.message)
        }
      }

      agent.client.onReceivedWebviewMessage = Consumer { params ->
        if (!project.isDisposed) {
          AgentChatSessionService.getInstance(project)
              .getSession(params.id)
              ?.receiveWebviewExtensionMessage(params.message)
        }
      }

      agent.client.onEditTaskDidUpdate = Consumer { task ->
        FixupService.getInstance(project).getActiveSession()?.update(task)
      }

      agent.client.onEditTaskDidDelete = Consumer { params ->
        FixupService.getInstance(project).getActiveSession()?.let {
          if (params.id == it.taskId) it.taskDeleted()
        }
      }

      agent.client.onWorkspaceEdit = Consumer { params ->
        FixupService.getInstance(project).getActiveSession()?.performWorkspaceEdit(params)
      }

      agent.client.onTextDocumentEdit = Consumer { params ->
        FixupService.getInstance(project).getActiveSession()?.performInlineEdits(params.edits)
      }

      agent.client.onTextDocumentShow = Function { params ->
        CodyEditorUtil.showDocument(
            project,
            URI.create(params.uri).withScheme("file"),
            params.options?.selection?.toVSCodeRange(),
            params.options?.preserveFocus)
      }

      agent.client.onOpenUntitledDocument = Function { params ->
        val uri = URI.create(params.uri).withScheme("file")
        if (CodyEditorUtil.createFileIfNeeded(project, uri, params.content) == null)
            return@Function false
        ApplicationManager.getApplication().invokeAndWait {
          CodyEditorUtil.showDocument(project, uri)
        }
        return@Function true
      }

      agent.client.onRemoteRepoDidChange = Consumer {
        RemoteRepoSearcher.getInstance(project).remoteRepoDidChange()
      }

      agent.client.onRemoteRepoDidChangeState = Consumer { state ->
        RemoteRepoSearcher.getInstance(project).remoteRepoDidChangeState(state)
      }

      agent.client.onIgnoreDidChange = Consumer {
        IgnoreOracle.getInstance(project).onIgnoreDidChange()
      }

      if (!project.isDisposed) {
        AgentChatSessionService.getInstance(project).restoreAllSessions(agent)
        CodyFileEditorListener.registerAllOpenedFiles(project, agent)
      }
    }
  }

  private fun onStartup(action: (CodyAgent) -> Unit) {
    synchronized(startupActions) { startupActions.add(action) }
  }

  fun startAgent(project: Project): CompletableFuture<CodyAgent> {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val future =
            CodyAgent.create(project).exceptionally { err ->
              val msg = "Creating agent unsuccessful: ${err.localizedMessage}"
              logger.error(msg)
              throw (CodyAgentException(msg))
            }
        val agent = future.get(45, TimeUnit.SECONDS)
        if (!agent.isConnected()) {
          val msg = "Failed to connect to agent Cody agent"
          logger.error(msg)
          throw CodyAgentException(msg) // This will be caught by the catch blocks below
        } else {
          synchronized(startupActions) { startupActions.forEach { action -> action(agent) } }
          codyAgent.complete(agent)
          CodyStatusService.resetApplication(project)
        }
      } catch (e: Exception) {
        val msg =
            if (e is TimeoutException)
                "Failed to start Cody agent in timely manner, please run any Cody action to retry"
            else "Failed to start Cody agent"
        logger.error(msg, e)
        setAgentError(project, msg)
        codyAgent.completeExceptionally(CodyAgentException(msg, e))
      }
    }
    return codyAgent
  }

  fun stopAgent(project: Project?) {
    try {
      codyAgent.getNow(null)?.shutdown()
    } catch (e: Exception) {
      logger.warn("Failed to stop Cody agent gracefully", e)
    } finally {
      codyAgent = CompletableFuture()
      project?.let { CodyStatusService.resetApplication(it) }
    }
  }

  fun restartAgent(project: Project): CompletableFuture<CodyAgent> {
    synchronized(this) {
      stopAgent(project)
      return startAgent(project)
    }
  }

  override fun dispose() {
    stopAgent(null)
  }

  companion object {
    private val logger = Logger.getInstance(CodyAgent::class.java)

    val agentError: AtomicReference<String?> = AtomicReference(null)

    @JvmStatic
    fun getInstance(project: Project): CodyAgentService {
      return project.service<CodyAgentService>()
    }

    @JvmStatic
    fun setAgentError(project: Project, e: Exception) {
      setAgentError(project, ((e.cause as? CodyAgentException) ?: e).message ?: e.toString())
    }

    @JvmStatic
    fun setAgentError(project: Project, errorMsg: String?) {
      val oldErrorMsg = agentError.getAndSet(errorMsg)
      if (oldErrorMsg != errorMsg) project.let { CodyStatusService.resetApplication(it) }
    }

    @JvmStatic
    private fun withAgent(
        project: Project,
        restartIfNeeded: Boolean,
        callback: Consumer<CodyAgent>,
        onFailure: Consumer<Exception> = Consumer {}
    ): CompletableFuture<Boolean> {
      val future = CompletableFuture<Boolean>()
      if (CodyApplicationSettings.instance.isCodyEnabled) {
        ApplicationManager.getApplication().executeOnPooledThread {
          runBlocking {
            val task: suspend (CodyAgent) -> Unit = { agent ->
              try {
                callback.accept(agent)
              } catch (e: Exception) {
                onFailure.accept(e)
              }
            }
            coWithAgent(project, restartIfNeeded, task)
          }
        }
      } else {
        future.complete(false) // Complete the future with false indicating Cody is disabled.
      }
      return future
    }

    @JvmStatic
    fun withAgent(project: Project, callback: Consumer<CodyAgent>): CompletableFuture<Boolean> =
        withAgent(project, restartIfNeeded = false, callback = callback)

    @JvmStatic
    fun withAgentRestartIfNeeded(
        project: Project,
        callback: Consumer<CodyAgent>
    ): CompletableFuture<Boolean> = withAgent(project, restartIfNeeded = true, callback = callback)

    @JvmStatic
    fun withAgentRestartIfNeeded(
        project: Project,
        callback: Consumer<CodyAgent>,
        onFailure: Consumer<Exception>
    ): CompletableFuture<Boolean> =
        withAgent(project, restartIfNeeded = true, callback = callback, onFailure = onFailure)

    @JvmStatic
    fun isConnected(project: Project): Boolean {
      return try {
        getInstance(project).codyAgent.getNow(null)?.isConnected() == true
      } catch (e: Exception) {
        false
      }
    }

    suspend fun <T> coWithAgent(project: Project, callback: suspend (CodyAgent) -> T) =
        coWithAgent(project, false, callback)

    suspend fun <T> coWithAgent(
        project: Project,
        restartIfNeeded: Boolean,
        callback: suspend (CodyAgent) -> T
    ): T {
      if (!CodyApplicationSettings.instance.isCodyEnabled) {
        throw Exception("Cody is not enabled")
      }
      try {
        val instance = CodyAgentService.getInstance(project)
        val isReadyButNotFunctional = instance.codyAgent.getNow(null)?.isConnected() == false
        val agent =
            if (isReadyButNotFunctional && restartIfNeeded) instance.restartAgent(project)
            else instance.codyAgent
        val result = callback(agent.get())
        setAgentError(project, null)
        return result
      } catch (e: Exception) {
        logger.warn("Failed to execute call to agent", e)
        if (restartIfNeeded && e !is ProcessCanceledException) {
          getInstance(project).restartAgent(project)
        }
        throw e
      }
    }
  }
}
