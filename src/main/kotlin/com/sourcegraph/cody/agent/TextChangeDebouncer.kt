package com.sourcegraph.cody.agent

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class TextChangeDebouncer(val project: Project) {

  private val scheduler = Executors.newScheduledThreadPool(1)
  private var lastUri: String? = null
  private var scheduledFuture: ScheduledFuture<*>? = null
  private val delay = DEBOUNCE_INTERVAL
  private val timeUnit = TimeUnit.MILLISECONDS

  @Synchronized
  fun debounce(document: ProtocolTextDocument, forceSkipDebounce: Boolean = false) {
    // If URI has changed (i.e. switched tabs or documents), send the first one immediately.
    if (BYPASS_DEBOUNCE || forceSkipDebounce || document.uri != lastUri) {
      scheduledFuture?.cancel(false)
      sendTextDocumentDidChange(document)
      lastUri = document.uri
      return
    }
    // New change arrived, so restart debounce timer.
    scheduledFuture?.cancel(false)
    scheduledFuture = scheduler.schedule({ sendTextDocumentDidChange(document) }, delay, timeUnit)
  }

  private fun sendTextDocumentDidChange(document: ProtocolTextDocument) {
    try {
      CodyAgentService.withAgent(project) { agent -> agent.server.textDocumentDidChange(document) }
    } catch (x: Exception) {
      logger.warn("Error in sendTextDocumentDidChange method for file: ${document.uri}", x)
    }
  }

  companion object {
    private val logger = Logger.getInstance(CodyAgent::class.java)

    // Here are some empirical numbers I found for the performance, by doing this
    // set of steps for different debounce intervals, all in the same fresh file:
    // - make a large selection quickly by dragging the mouse
    // - hammer on the keys to remove the selected text and fill about 80 columns
    // - hold the arrow key down to move the caret about 30 lines as fast as it goes
    //
    // Results for how many times sendTextDocumentDidChange above is called,
    // averaging 2 test runs for each config:
    //   0ms debounce: 270 times
    //   5ms debounce: 190 times
    //  10ms debounce: 150 times
    //  30ms debounce: 40 times -- roughly 33 calls per second. Good enough.
    private const val DEBOUNCE_INTERVAL = 30L

    // A flag for turning debounce entirely off, for comparing performance, debugging, etc.
    private val BYPASS_DEBOUNCE: Boolean =
        System.getProperty("cody.bypassTextChangeDebounce") == "true"
  }
}
