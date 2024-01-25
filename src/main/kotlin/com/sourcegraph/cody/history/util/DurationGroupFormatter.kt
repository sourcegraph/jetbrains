package com.sourcegraph.cody.history.util

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object DurationGroupFormatter {

  fun format(since: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val days = ChronoUnit.DAYS.between(since, now).toInt()
    val weeks = ChronoUnit.WEEKS.between(since, now).toInt()
    val months = ChronoUnit.MONTHS.between(since, now).toInt()
    val years = ChronoUnit.YEARS.between(since, now).toInt()
    return when {
      days == 0 -> "Today"
      days == 1 -> "Yesterday"
      days in 2..6 -> "$days days ago"
      months == 1 -> "Last month"
      weeks == 1 -> "Last week"
      weeks in 2..4 -> "$weeks weeks ago"
      years == 1 -> "Last year"
      months in 2..12 -> "$months months ago"
      else -> "$years years ago"
    }
  }
}
