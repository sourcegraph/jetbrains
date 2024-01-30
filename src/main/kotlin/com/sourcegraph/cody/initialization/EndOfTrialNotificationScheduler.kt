package com.sourcegraph.cody.initialization

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
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

class EndOfTrialNotificationScheduler private constructor(val project: Project) : Disposable {

  private val logger = Logger.getInstance(EndOfTrialNotificationScheduler::class.java)

  private val scheduler = Executors.newScheduledThreadPool(1)

  private var times = 1

  init {
    scheduler.scheduleAtFixedRate(
        /* command = */ {
          if (!ConfigUtil.isCodyEnabled()) {
            return@scheduleAtFixedRate
          }

          if (project.isDisposed) {
            this.dispose()
          }

          if (CodyAuthenticationManager.instance.getActiveAccount(project)?.isDotcomAccount() ==
              false) {
            return@scheduleAtFixedRate
          }

          CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
            agent.server
                .getCurrentUserCodySubscription()
                .thenApply { currentUserCodySubscription ->
                  if (currentUserCodySubscription == null) {
                    logger.warn("currentUserCodySubscription is null")
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
                            showProperNotificationIfApplicable(
                                currentUserCodySubscription = currentUserCodySubscription,
                                codyProTrialEnded = times-- < 0,
                                useSscForCodySubscription = true)
                          }
                }
                .completeOnTimeout(null, 4, TimeUnit.SECONDS)
          }
        },
        /* initialDelay = */ 0,
        /* period = */ 29,
        /* unit = */ TimeUnit.SECONDS)
  }

  private fun showProperNotificationIfApplicable(
      currentUserCodySubscription: CurrentUserCodySubscription,
      codyProTrialEnded: Boolean,
      useSscForCodySubscription: Boolean
  ) {
    if (currentUserCodySubscription.plan == Plan.PRO &&
        currentUserCodySubscription.status == Status.PENDING &&
        useSscForCodySubscription) {
      if (codyProTrialEnded) {
        if (PropertiesComponent.getInstance().getBoolean(TrialEndedNotification.ignore)) {
          dispose()
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

  override fun dispose() {
    scheduler.shutdown()
  }

  companion object {
    fun createAndStart(project: Project): EndOfTrialNotificationScheduler {
      return EndOfTrialNotificationScheduler(project)
    }
  }
}
