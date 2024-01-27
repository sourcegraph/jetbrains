package com.sourcegraph.cody.initialization

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.CurrentUserCodySubscription
import com.sourcegraph.cody.agent.protocol.GetFeatureFlag
import com.sourcegraph.cody.agent.protocol.Plan
import com.sourcegraph.cody.agent.protocol.Status
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.config.ConfigUtil
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EndOfTrialNotificationScheduler private constructor(project: Project) : Disposable {
  private val scheduler = Executors.newScheduledThreadPool(1)

  init {
    scheduler.scheduleAtFixedRate(
        /* command = */ {
          if (!ConfigUtil.isCodyEnabled()) {
            return@scheduleAtFixedRate
          }

          if (project.isDisposed) {
            this.dispose()
          }

          CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
            agent.server
                .getCurrentUserCodySubscription()
                .thenApply { currentUserCodySubscription ->
                  if (currentUserCodySubscription == null) {
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
                                currentUserCodySubscription = currentUserCodySubscription,
                                codyProTrialEnded ?: false,
                                useSscForCodySubscription ?: false)
                          }
                }
                .completeOnTimeout(null, 4, TimeUnit.SECONDS)
          }
        },
        /* initialDelay = */ 0,
        /* period = */ 2,
        /* unit = */ TimeUnit.HOURS)
  }

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
          TrialEndedNotification(disposable = this).notify(project)
        } else {
          if (PropertiesComponent.getInstance().getBoolean(TrialEndingSoonNotification.ignore)) {
            return
          }
          TrialEndingSoonNotification().notify(project)
        }
      }
    }
  }

  override fun dispose() {
    scheduler.shutdown()
  }

  companion object {
    fun createAndStart(project: Project): EndOfTrialNotificationScheduler {
      return EndOfTrialNotificationScheduler(project)
    }
  }
}
