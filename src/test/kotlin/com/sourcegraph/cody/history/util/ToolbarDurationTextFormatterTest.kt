package com.sourcegraph.cody.history.util

import java.time.LocalDateTime
import junit.framework.TestCase

class ToolbarDurationTextFormatterTest : TestCase() {

  fun `test duration`() {
    assertEquals("12s", formatDurationSinceEpoch("1970-01-01T00:00:12"))
    assertEquals("7m", formatDurationSinceEpoch("1970-01-01T00:07:14"))
    assertEquals("4h", formatDurationSinceEpoch("1970-01-01T04:12:04"))
    assertEquals("12h", formatDurationSinceEpoch("1970-01-01T12:04:17"))
    assertEquals("5d", formatDurationSinceEpoch("1970-01-06T04:12:57"))
    assertEquals("162d", formatDurationSinceEpoch("1970-06-12T01:23:45"))
  }

  private fun formatDurationSinceEpoch(now: String) =
      ToolbarDurationTextFormatter.formatDuration(
          lastUpdated = LocalDateTime.parse("1970-01-01T00:00:00"), now = LocalDateTime.parse(now))
}
