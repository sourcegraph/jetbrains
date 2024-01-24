package com.sourcegraph.cody.agent.protocol

import java.util.*

data class CurrentUserCodySubscription(
    val status: String, // todo: enums
    val plan: String, // todo: "PRO", "FREE" enums
    val applyProRateLimits: String,
    val currentPeriodStartAt: Date,
    val currentPeriodEndAt: Date,
)
