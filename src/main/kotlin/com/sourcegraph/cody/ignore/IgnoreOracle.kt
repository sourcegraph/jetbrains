package com.sourcegraph.cody.ignore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.SLRUMap
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.IgnoreForUriParams
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

enum class IgnorePolicy(val value: String) {
  IGNORE("ignore"),
  USE("use"),
}

/**
 * Provides details about whether files and repositories must be ignored as chat context, for autocomplete, etc. per
 * policy.
 */
@Service(Service.Level.PROJECT)
class IgnoreOracle(private val project: Project) {
  companion object {
    fun getInstance(project: Project): IgnoreOracle {
      return project.service<IgnoreOracle>()
    }
  }

  private val logger = LoggerFactory.getLogger(IgnoreOracle::class.java)
  private val listeners = mutableListOf<IgnorePolicyListener>()
  private val cache = SLRUMap<String, IgnorePolicy>(100, 100)

  /**
   * Notifies the IgnoreOracle that the ignore policy has changed. Called by CodyAgentService's client callbacks.
   */
  fun onIgnoreDidChange() {
    synchronized(cache) {
      cache.clear()
    }
    firePolicyChange()
  }

  /**
   * Gets whether `uri` should be ignored for autocomplete, context, etc.
   */
  fun policyForUri(uri: String): CompletableFuture<IgnorePolicy> {
    val completable = CompletableFuture<IgnorePolicy>()
    synchronized (cache) {
      try {
        completable.complete(cache[uri])
        return completable
      } catch (e: NoSuchElementException) {
        // ignore, fall through
      }
    }
    CodyAgentService.withAgent(project) { agent ->
      val policy = when (agent.server.ignoreForUri(IgnoreForUriParams(uri)).get().policy) {
        "ignore" -> IgnorePolicy.IGNORE
        "use" -> IgnorePolicy.USE
        else -> throw Exception("invalid ignore policy value")
      }
      synchronized(cache) {
        cache.put(uri, policy)
      }
      completable.complete(policy)
    }
    return completable
  }

  /**
   * Adds a listener. Thread safe. Listener will not receive any events that are already in the process of being
   * dispatched.
   */
  fun addListener(listener: IgnorePolicyListener) {
    synchronized(listeners) {
      listeners.add(listener)
    }
  }

  /**
   * Removes a listener. Thread safe. If this is called during event dispatch, the listener may still recieve a
   * notification.
   */
  fun removeListener(listener: IgnorePolicyListener) {
    synchronized(listeners) {
      listeners.remove(listener)
    }
  }

  private fun firePolicyChange() {
    val toNotify = synchronized (listeners) {
      listeners.toList()
    }
    ApplicationManager.getApplication().executeOnPooledThread {
      toNotify.forEach {
        try {
          it.onPolicyChange()
        } catch (e: Exception) {
          logger.error("IgnoreOracle listener threw exception responding to policy change", e)
        }
      }
    }
  }
}

/**
 * Callback for detecting and reacting to changes in the policy of which files and repositories to ignore.
 */
interface IgnorePolicyListener {
  /**
   * Notifies the listener that the ignore policy has changed. Called on a pooled thread.
   */
  @RequiresBackgroundThread
  fun onPolicyChange()
}
