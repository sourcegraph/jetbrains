package com.sourcegraph.cody.auth.ui

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.sourcegraph.cody.auth.Account
import com.sourcegraph.cody.auth.AccountDetails
import com.sourcegraph.cody.auth.SingleValueModel
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

abstract class LoadingAccountsDetailsProvider<in A : Account, D : AccountDetails>(
    private val progressIndicatorsProvider: ProgressIndicatorsProvider
) : AccountsDetailsProvider<A, D> {

  open val defaultIcon: Icon = IconUtil.resizeSquared(EmptyIcon.ICON_16, 40)
  private val detailsMap = mutableMapOf<A, CompletableFuture<DetailsLoadingResult<D>>>()
  override val loadingStateModel = SingleValueModel(false)

  private var runningProcesses = 0

  override fun getDetails(account: A): D? = getOrLoad(account).getNow(null)?.details

  private fun getOrLoad(account: A): CompletableFuture<DetailsLoadingResult<D>> {
    return detailsMap.getOrPut(account) {
      val indicator = progressIndicatorsProvider.acquireIndicator()
      runningProcesses++
      loadingStateModel.value = true
      scheduleLoad(account, indicator)
          .whenComplete { _, _ ->
            ApplicationManager.getApplication().invokeAndWait {
              progressIndicatorsProvider.releaseIndicator(indicator)
              runningProcesses--
              if (runningProcesses == 0) loadingStateModel.value = false
            }
          }
          .exceptionally { it ->
            val error = CompletableFutureUtil.extractError(it)
            val errorMessage =
                error.localizedMessage.takeWhile { c -> c.isLetterOrDigit() || c.isWhitespace() }
            DetailsLoadingResult(null, null, errorMessage, false)
          }
    }
  }

  abstract fun scheduleLoad(
      account: A,
      indicator: ProgressIndicator
  ): CompletableFuture<DetailsLoadingResult<D>>

  override fun getAvatarImage(account: A): Image? = getOrLoad(account).getNow(null)?.avatarImage

  override fun getErrorText(account: A): String? = getOrLoad(account).getNow(null)?.error

  override fun checkErrorRequiresReLogin(account: A) =
      getOrLoad(account).getNow(null)?.needReLogin ?: false

  override fun reset(account: A) {
    detailsMap.remove(account)
  }

  override fun resetAll() = detailsMap.clear()

  data class DetailsLoadingResult<D : AccountDetails>(
      val details: D?,
      val avatarImage: Image?,
      @Nls val error: String?,
      val needReLogin: Boolean
  )
}
