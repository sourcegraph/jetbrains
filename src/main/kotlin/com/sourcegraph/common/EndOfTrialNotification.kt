package com.sourcegraph.common

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.CurrentUserCodySubscription
import com.sourcegraph.cody.agent.protocol.GetFeatureFlag
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.config.ConfigUtil
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EndOfTrialNotification {
  companion object {
    private fun showEndOfTrialNotificationIfApplicable(
        project: Project,
        currentUserCodySubscription: CurrentUserCodySubscription,
        codyProTrialEnded: Boolean,
        useSscForCodySubscription: Boolean
    ) {
      val activeAccountType = CodyAuthenticationManager.instance.getActiveAccount(project)
      if (activeAccountType != null && activeAccountType.isDotcomAccount()) {

        if (currentUserCodySubscription.plan == Plan.PRO &&
            currentUserCodySubscription.status == Status.PENDING &&
            useSscForCodySubscription) {
          if (codyProTrialEnded) {
            if (PropertiesComponent.getInstance().getBoolean(TrialEndedNotification.ignore)) {
              return
            }
            TrialEndedNotification().notify(project)
          } else {
            if (PropertiesComponent.getInstance().getBoolean(TrialEndingSoonNotification.ignore)) {
              return
            }
            TrialEndingSoonNotification().notify(project)
          }
        }
      }
    }

    fun startScheduler(project: Project) {
      val scheduler = Executors.newScheduledThreadPool(1)
      scheduler.scheduleAtFixedRate(
          {
            if (!ConfigUtil.isCodyEnabled()) {
              return@scheduleAtFixedRate
            }

            CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
              agent.server
                  .getCurrentUserCodySubscription()
                  .thenApply {
                    if (it == null) {
                      // todo: proper handling
                      println("getCurrentUserCodySubscription returned null")
                      return@thenApply
                    }

                    agent.server
                        .evaluateFeatureFlag(GetFeatureFlag.CodyProTrialEnded)
                        .completeOnTimeout(false, 4, TimeUnit.SECONDS)
                        .thenCombine(
                            agent.server
                                .evaluateFeatureFlag(GetFeatureFlag.UseSscForCodySubscription)
                                .completeOnTimeout(false, 4, TimeUnit.SECONDS)) {
                                codyProTrialEnded,
                                useSscForCodySubscription ->
                              showEndOfTrialNotificationIfApplicable(
                                  project,
                                  currentUserCodySubscription = it,
                                  codyProTrialEnded ?: false,
                                  useSscForCodySubscription ?: false)
                              scheduler.shutdown() // todo: ensure it running after ending soon dismissed
                            }
                  }
                  .completeOnTimeout(null, 4, TimeUnit.SECONDS)
            }
          },
          0,
          2,
          TimeUnit.HOURS)
    }
  }
}
