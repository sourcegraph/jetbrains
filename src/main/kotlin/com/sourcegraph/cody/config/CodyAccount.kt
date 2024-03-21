package com.sourcegraph.cody.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.auth.ServerAccount
import com.sourcegraph.config.ConfigUtil
import java.util.concurrent.CompletableFuture

enum class AccountType {
  DOTCOM,
  ENTERPRISE
}

@Tag("account")
data class CodyAccount(
    @NlsSafe @Attribute("name") override var name: String = "",
    @Attribute("displayName") var displayName: String? = null,
    @Property(style = Property.Style.ATTRIBUTE, surroundWithTag = false)
    override val server: SourcegraphServerPath =
        SourcegraphServerPath.from(ConfigUtil.DOTCOM_URL, ""),
    @Attribute("id") override var id: String = generateId(),
) : ServerAccount() {

  @Volatile private var isProUser: Boolean? = null

  fun isDotcomAccount(): Boolean = server.url.lowercase().startsWith(ConfigUtil.DOTCOM_URL)

  fun isEnterpriseAccount(): Boolean = isDotcomAccount().not()

  fun isFreeUser(project: Project): CompletableFuture<Boolean> =
      if (isDotcomAccount()) isProUser(project).thenApply { it.not() }
      else CompletableFuture.completedFuture(false)

  fun isProUser(project: Project): CompletableFuture<Boolean> {
    if (!isDotcomAccount()) return CompletableFuture.completedFuture(false)
    if (isProUser != null) return CompletableFuture.completedFuture(isProUser)

    val isProUserFuture = CompletableFuture<Boolean>()
    CodyAgentService.withAgent(project) { agent ->
      isProUser = agent.server.isCurrentUserPro().get()
      isProUserFuture.complete(isProUser)
    }

    return isProUserFuture
  }

  override fun toString(): String = "$server/$name"

  companion object {
    fun create(
        username: String,
        displayName: String?,
        server: SourcegraphServerPath,
        id: String = generateId(),
    ): CodyAccount {
      return CodyAccount(username, displayName ?: username, server, id)
    }
  }
}

fun Collection<CodyAccount>.getFirstAccountOrNull() =
    this.firstOrNull { it.isDotcomAccount() } ?: this.firstOrNull()
