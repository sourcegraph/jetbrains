package com.sourcegraph.cody.autocomplete

import com.sourcegraph.cody.agent.protocol.CompletionBookkeepingEvent
import com.sourcegraph.cody.agent.protocol.CompletionItemID

/** A class that stores the state and timing information of an autocompletion. */
class AutocompleteTelemetry {
  // TODO: we could use java.time.Instant.now() and java.time.Duration.between(Instant,Instant)
  // and avoid the "TimestampMs" suffixes
  private var completionTriggeredTimestampMs: Long = System.currentTimeMillis()
  private var completionDisplayedTimestampMs: Long = 0
  private var completionHiddenTimestampMs: Long = 0
  private var completionEvent: CompletionBookkeepingEvent? = null
  var logID: CompletionItemID? = null
    private set

  fun markCompletionDisplayed() {
    completionDisplayedTimestampMs = System.currentTimeMillis()
  }

  val latencyMs: Long
    get() = completionDisplayedTimestampMs - completionTriggeredTimestampMs

  fun markCompletionEvent(logID: CompletionItemID?, event: CompletionBookkeepingEvent?) {
    this.logID = logID
    completionEvent = event
  }

  fun markCompletionHidden() {
    completionHiddenTimestampMs = System.currentTimeMillis()
  }

  val displayDurationMs: Long
    get() = completionHiddenTimestampMs - completionDisplayedTimestampMs

  fun params(): CompletionBookkeepingEvent.Params? {
    return if (completionEvent != null) completionEvent!!.params else null
  }

  val status: AutocompletionStatus
    get() {
      if (completionDisplayedTimestampMs == 0L) {
        return AutocompletionStatus.TRIGGERED_NOT_DISPLAYED
      }
      return if (completionHiddenTimestampMs == 0L) {
        AutocompletionStatus.DISPLAYED
      } else AutocompletionStatus.HIDDEN
    }
}
