package com.sourcegraph.cody.agent.protocol

object GetCurrentUserCodySubscription {
  object Plan {
    const val PRO = "PRO"
    const val FREE = "FREE"
  }

  object Status {
    const val ACTIVE = "ACTIVE"
    const val PAST_DUE = "PAST_DUE"
    const val UNPAID = "UNPAID"
    const val CANCELED = "CANCELED"
    const val TRIALING = "TRIALING"
    const val PENDING = "PENDING"
    const val OTHER = "OTHER"
  }
}
