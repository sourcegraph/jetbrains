package com.sourcegraph.cody.ignore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.SLRUMap
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.IgnoreTestParams
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import com.sourcegraph.cody.statusbar.CodyStatusService
import com.sourcegraph.utils.CodyEditorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class IgnorePolicy(val value: String) {
  IGNORE("ignore"),
  USE("use"),
}

/**
 * Provides details about whether files and repositories must be ignored as chat context, for
 * autocomplete, etc. per policy.
 */
@Service(Service.Level.PROJECT)
class IgnoreOracle(private val project: Project) {
  private val logger = Logger.getInstance(IgnoreOracle::class.java)

  data class CacheEntry(val policy: IgnorePolicy, val timestampMsec: Long)

  private val cache = SLRUMap<String, CacheEntry>(100, 100)
  @Volatile private var focusedPolicy: IgnorePolicy? = null
  @Volatile private var willFocusUri: String? = null
  private val fileListeners: MutableList<FocusedFileIgnorePolicyListener> = mutableListOf()
  private val policyAwaitTimeoutMs = System.getProperty("cody.ignore.policy.timeout", "16").toLong()

  init {
    // Synthesize a focus event for the current editor, if any,
    // to fetch and cache ignore state for it.
    runInEdt {
      CodyEditorUtil.getSelectedEditors(project).forEach { editor ->
        if (willFocusUri == null) {
          val uri = ProtocolTextDocument.fromEditor(editor)?.uri
          if (uri != null) {
            focusedFileDidChange(uri)
          }
        }
      }
    }
  }

  val isEditingIgnoredFile: Boolean
    get() {
      return focusedPolicy == IgnorePolicy.IGNORE
    }

  fun focusedFileDidChange(uri: String) {
    willFocusUri = uri
    ApplicationManager.getApplication().executeOnPooledThread {
      val policy = policyForUri(uri).get()
      if (focusedPolicy != policy && willFocusUri == uri) {
        focusedPolicy = policy

        // Update the status bar.
        CodyStatusService.resetApplication(project)

        val listeners = synchronized(fileListeners) { fileListeners.toList() }
        for (listener in listeners) {
          listener.focusedFileIgnorePolicyChanged(policy)
        }
      }
    }
  }

  fun addListener(listener: FocusedFileIgnorePolicyListener) {
    synchronized(fileListeners) { fileListeners.add(listener) }
    // Invoke the listener with the focused file policy to set initial state.
    listener.focusedFileIgnorePolicyChanged(focusedPolicy ?: IgnorePolicy.USE)
  }

  fun removeListener(listener: FocusedFileIgnorePolicyListener) {
    synchronized(fileListeners) { fileListeners.remove(listener) }
  }

  /**
   * Notifies the IgnoreOracle that the ignore policy has changed. Called by CodyAgentService's
   * client callbacks.
   */
  fun onIgnoreDidChange() {
    synchronized(cache) { cache.clear() }

    // Re-set the focused file URI to update the status bar.
    val uri = willFocusUri
    if (uri != null) {
      focusedFileDidChange(uri)
    }
  }

  /** Gets whether `uri` should be ignored for autocomplete, context, etc. */
  fun policyForUri(uri: String): CompletableFuture<IgnorePolicy> {
    val completable = CompletableFuture<IgnorePolicy>()
    val result = synchronized(cache) { cache[uri] }
    val cacheMaxAgeMsec = 60 * 1000
    if (result != null && result.timestampMsec > System.currentTimeMillis() - cacheMaxAgeMsec) {
      completable.complete(result.policy)
      return completable
    }
    CodyAgentService.withAgent(project) { agent ->
      policyForUri(uri, agent).thenAccept(completable::complete)
    }
    return completable
  }

  /** Like `policyForUri(String)` but reuses the current thread and supplied Agent handle. */
  fun policyForUri(uri: String, agent: CodyAgent): CompletableFuture<IgnorePolicy> {
    return agent.server.ignoreTest(IgnoreTestParams(uri)).thenApply {
      val newPolicy =
          when (it.policy) {
            "ignore" -> IgnorePolicy.IGNORE
            "use" -> IgnorePolicy.USE
            else -> throw IllegalStateException("invalid ignore policy value")
          }
      synchronized(cache) {
        cache.put(uri, CacheEntry(policy = newPolicy, timestampMsec = System.currentTimeMillis()))
      }
      newPolicy
    }
  }

  /** Like `policyForUri(String)` but fetches the uri from the passed Editor's Document. */
  fun policyForEditor(editor: Editor): IgnorePolicy? {
    val url = FileDocumentManager.getInstance().getFile(editor.document)?.url ?: return null
    val completable = policyForUri(url)
    return try {
      completable.get(policyAwaitTimeoutMs, TimeUnit.MILLISECONDS)
    } catch (timedOut: TimeoutException) {
      logger.warn(timedOut)
      null
    }
  }

  interface FocusedFileIgnorePolicyListener {
    fun focusedFileIgnorePolicyChanged(policy: IgnorePolicy)
  }

  companion object {
    fun getInstance(project: Project): IgnoreOracle {
      return project.service<IgnoreOracle>()
    }
  }
}
