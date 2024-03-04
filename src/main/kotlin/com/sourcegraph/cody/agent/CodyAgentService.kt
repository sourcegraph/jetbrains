package com.sourcegraph.cody.agent

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.CodyFileEditorListener
import com.sourcegraph.cody.chat.AgentChatSessionService
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.statusbar.CodyAutocompleteStatusService
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

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

      if (!project.isDisposed) {
        FileEditorManager.getInstance(project).openFiles.forEach { file ->
          CodyFileEditorListener.fileOpened(project, agent, file)
        }
      }
    }
  }

  private fun onStartup(action: (CodyAgent) -> Unit) {
    synchronized(startupActions) { startupActions.add(action) }
  }

  private fun getInitializedAgent(
      project: Project,
      restartIfNeeded: Boolean
  ): CompletableFuture<CodyAgent> {
    return try {
      val isReadyButNotFunctional = codyAgent.getNow(null)?.isConnected() == false
      if (isReadyButNotFunctional && restartIfNeeded) restartAgent(project) else codyAgent
    } catch (e: Exception) {
      if (restartIfNeeded) restartAgent(project) else codyAgent
    }
  }

  fun startAgent(project: Project): CompletableFuture<CodyAgent> {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val agent = CodyAgent.create(project).get(25, TimeUnit.SECONDS)
        if (!agent.isConnected()) {
          val msg = "Failed to connect to agent Cody agent"
          logger.error(msg)
          codyAgent.completeExceptionally(Exception(msg))
        } else {
          synchronized(startupActions) { startupActions.forEach { action -> action(agent) } }
          codyAgent.complete(agent)
          CodyAutocompleteStatusService.resetApplication(project)
        }
      } catch (e: Exception) {
        val msg = "Failed to start Cody agent"
        logger.error(msg, e)
        codyAgent.completeExceptionally(Exception(msg, e))
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
      project?.let { CodyAutocompleteStatusService.resetApplication(it) }
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
    fun setAgentError(project: Project, errorMsg: String) {
      agentError.set(errorMsg)
      project.let { CodyAutocompleteStatusService.resetApplication(it) }
    }

    @JvmStatic
    private fun withAgent(
        project: Project,
        restartIfNeeded: Boolean,
        callback: Consumer<CodyAgent>
    ) {
      if (CodyApplicationSettings.instance.isCodyEnabled) {
        agentError.set(null)
        ApplicationManager.getApplication().executeOnPooledThread {
          val agent =
              getInstance(project)
                  .getInitializedAgent(project, restartIfNeeded = restartIfNeeded)
                  .get()
          try {
            callback.accept(agent)
          } catch (e: ExecutionException) {
            val cause = e.cause as? ResponseErrorException ?: throw e
            setAgentError(project, cause.message ?: e.toString())
            logger.warn("Failed to execute call to agent", cause)
          }
        }
      }
    }

    @JvmStatic
    fun withAgent(project: Project, callback: Consumer<CodyAgent>) =
        withAgent(project, restartIfNeeded = false, callback = callback)

    @JvmStatic
    fun withAgentRestartIfNeeded(project: Project, callback: Consumer<CodyAgent>) =
        withAgent(project, restartIfNeeded = true, callback = callback)

    @JvmStatic
    fun isConnected(project: Project): Boolean {
      return getInstance(project)
          .getInitializedAgent(project, restartIfNeeded = false)
          .getNow(null)
          ?.isConnected() == true
    }
  }
}
