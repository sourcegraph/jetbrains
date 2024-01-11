package com.sourcegraph.cody

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentManager
import com.sourcegraph.cody.agent.CodyAgentServer
import com.sourcegraph.cody.agent.protocol.GetFeatureFlag
import com.sourcegraph.cody.config.CodyAuthenticationManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class SubscriptionTabPanelData(
    val isDotcomAccount: Boolean,
    val codyProFeatureFlag: Boolean,
    val isCurrentUserPro: Boolean?
)

@RequiresBackgroundThread
fun fetchSubscriptionPanelData(project: Project): CompletableFuture<SubscriptionTabPanelData?> {
  val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
  if (activeAccountType != null) {
    ensureUserIdMatchInAgent(project, activeAccountType.id)

    return CodyAgent.getInitializedServer(project).thenCompose { server ->
      if (activeAccountType.isDotcomAccount()) {
        val codyProFeatureFlag = server.evaluateFeatureFlag(GetFeatureFlag("CodyProJetBrains"))
        if (codyProFeatureFlag.get() != null && codyProFeatureFlag.get()!!) {
          val isCurrentUserPro = getIsCurrentUserPro(server) ?: false
          CompletableFuture.completedFuture(
              SubscriptionTabPanelData(
                  activeAccountType.isDotcomAccount(),
                  codyProFeatureFlag = true,
                  isCurrentUserPro = isCurrentUserPro))
        } else {
          CompletableFuture.completedFuture(
              SubscriptionTabPanelData(
                  activeAccountType.isDotcomAccount(),
                  codyProFeatureFlag = false,
                  isCurrentUserPro = null))
        }
      } else {
        CompletableFuture.completedFuture(
            SubscriptionTabPanelData(
                activeAccountType.isDotcomAccount(),
                codyProFeatureFlag = false,
                isCurrentUserPro = false))
      }
    }
  }
  return CompletableFuture.completedFuture(null)
}

@RequiresBackgroundThread
private fun ensureUserIdMatchInAgent(project: Project, jetbrainsUserId: String) {
  var agentUserId: String? = null
  var agentRestartRetryCount = 2
  do {
    CodyAgentManager.tryRestartingAgentIfNotRunning(project)
    CodyAgent.getInitializedServer(project)
        .thenApply { server ->
          agentUserId = getUserId(server)

          var retryCount = 3
          while (jetbrainsUserId != agentUserId && retryCount > 0) {
            Thread.sleep(200)
            retryCount--
            CodyToolWindowContent.logger.warn("Retrying call for userId from agent")
            agentUserId = getUserId(server)
          }
        }[3, TimeUnit.SECONDS]

    if (jetbrainsUserId != agentUserId) {
      if (agentUserId != null) {
        CodyToolWindowContent.logger.warn(
            "User id in JetBrains is different from agent: restarting agent...")
      } else {
        CodyToolWindowContent.logger.warn("User id in Agent is null: restarting agent...")
      }
      CodyAgentManager.restartAgent(project)
    } else {
      return
    }

    agentRestartRetryCount--
  } while (agentRestartRetryCount > 0)
}

@RequiresBackgroundThread
private fun getUserId(server: CodyAgentServer): String? =
    server
        .currentUserId()
        .exceptionally {
          CodyToolWindowContent.logger.warn("Unable to fetch user id from agent")
          null
        }
        .get()

@RequiresBackgroundThread
private fun getIsCurrentUserPro(server: CodyAgentServer): Boolean? =
    server
        .isCurrentUserPro()
        .exceptionally { e ->
          CodyToolWindowContent.logger.warn("Error getting user pro status", e)
          null
        }
        .get()
