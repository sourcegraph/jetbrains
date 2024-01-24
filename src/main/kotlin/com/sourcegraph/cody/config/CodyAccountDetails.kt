package com.sourcegraph.cody.config

import com.sourcegraph.cody.agent.protocol.CurrentUserCodySubscription
import com.sourcegraph.cody.auth.AccountDetails

class CodyAccountDetails(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarURL: String?,
    val currentUserCodySubscription: CurrentUserCodySubscription?
) : AccountDetails {
  override val name: String
    get() = displayName ?: username
}
