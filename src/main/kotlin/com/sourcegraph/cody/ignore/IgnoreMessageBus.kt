package com.sourcegraph.cody.ignore

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic

interface IgnoreMessageBus {

  // Information passed to subscribers.
  data class Context(val project: Project, val uri: String, val policy: IgnorePolicy?)

  /**
   * Run before a policy change for a specific uri. Policy in context is old policy, which may be
   * null if it had not previously been computed. Note that this may be called multiple times,
   * possibly with different values, before the afterAction call, due to nuances in the project
   * initialization.
   */
  @RequiresEdt fun beforeAction(context: Context)

  /**
   * Run after a policy change. Policy in context is old policy, which may be null if it had not
   * previously been computed. Note that this may be called multiple times with the same value for a
   * given uri, without a corresponding beforeAction call, so the implementation should be
   * idempotent.
   */
  @RequiresEdt fun afterAction(context: Context)

  companion object {
    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_IGNORE_POLICY_UPDATE =
        Topic.create("Sourcegraph Cody: Ignore policy updated", IgnoreMessageBus::class.java)
  }
}

open class IgnoreMessageBusAdapter : IgnoreMessageBus {
  override fun beforeAction(context: IgnoreMessageBus.Context) {}

  override fun afterAction(context: IgnoreMessageBus.Context) {}
}
