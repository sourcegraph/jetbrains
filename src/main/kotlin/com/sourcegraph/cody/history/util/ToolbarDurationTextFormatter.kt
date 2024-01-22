package com.sourcegraph.cody.history.util

import java.time.Duration
import java.time.LocalDateTime
import kotlin.time.DurationUnit.*
import kotlin.time.toKotlinDuration

object ToolbarDurationTextFormatter {

  fun formatDuration(lastUpdated: LocalDateTime, now: LocalDateTime): String {
    val duration = Duration.between(lastUpdated, now).toKotlinDuration()
    return when {
      duration.inWholeSeconds < 60 -> duration.toString(SECONDS)
      duration.inWholeMinutes < 60 -> duration.toString(MINUTES)
      duration.inWholeHours < 24 -> duration.toString(HOURS)
      else -> duration.toString(DAYS)
    }
  }
}
