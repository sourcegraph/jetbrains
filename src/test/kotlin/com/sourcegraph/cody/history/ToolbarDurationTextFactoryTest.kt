package com.sourcegraph.cody.history

import com.sourcegraph.cody.history.util.ToolbarDurationTextFactory
import java.time.LocalDateTime
import junit.framework.TestCase

class ToolbarDurationTextFactoryTest : TestCase() {

  fun `test duration`() {
    assertEquals("12s", getDurationText("1970-01-01T00:00:12"))
    assertEquals("7m", getDurationText("1970-01-01T00:07:14"))
    assertEquals("4h", getDurationText("1970-01-01T04:12:04"))
    assertEquals("12h", getDurationText("1970-01-01T12:04:17"))
    assertEquals("5d", getDurationText("1970-01-06T04:12:57"))
    assertEquals("162d", getDurationText("1970-06-12T01:23:45"))
  }

  private fun getDurationText(now: String) =
      ToolbarDurationTextFactory.getDurationText(
          lastUpdated = LocalDateTime.parse("1970-01-01T00:00:00"), now = LocalDateTime.parse(now))
}
