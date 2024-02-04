package com.sourcegraph.cody.attribution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.CurrentConfigFeatures
import com.sourcegraph.cody.agent.protocol.AttributionSearchParams
import com.sourcegraph.cody.agent.protocol.AttributionSearchResponse
import com.sourcegraph.cody.chat.AgentChatSession
import com.sourcegraph.cody.chat.AgentChatSessionService
import com.sourcegraph.cody.chat.ui.CodeEditorPart
import java.util.*
import java.util.function.BiFunction

/**
 * [AttributionSearchCommand] performs attribution search on a code snippet, and then notifies of
 * the result.
 */
class AttributionSearchCommand(private val project: Project) {

  companion object {
    private val logger = Logger.getInstance(AttributionSearchCommand::class.java)
  }

  /**
   * [onSnippetFinished] invoked when assistant finished writing a code snippet in a chat message,
   * and triggers attribution search (if enabled). Once attribution returns, the
   * [CodeEditorPart.attributionListener] is updated.
   */
  fun onSnippetFinished(snippet: String, messageId: UUID, listener: AttributionListener) {
    if (attributionEnabled()) {
      val sessionId = findChatSessionFor(messageId)?.getSessionId()
      if (sessionId == null) {
        logger.warn("Unknown message chat session.")
        return
      }
      CodyAgentService.applyAgentOnBackgroundThread(project) { agent ->
        ApplicationManager.getApplication().invokeLater { listener.onAttributionSearchStart() }
        val params = AttributionSearchParams(id = sessionId, snippet = snippet)
        agent.server.attributionSearch(params).handle(updateEditor(listener))
      }
    }
  }

  /**
   * [updateEditor] returns a future handler for attribution search operation, which notifies the
   * listener.
   */
  private fun updateEditor(listener: AttributionListener) =
      BiFunction<AttributionSearchResponse?, Throwable?, Unit> { response, throwable ->
        listener.updateAttribution(
            response
                ?: AttributionSearchResponse(
                    error = throwable?.message ?: "Error searching for attribution.",
                    repoNames = listOf(),
                    limitHit = false,
                ))
      }

  private fun attributionEnabled(): Boolean =
      project.getService(CurrentConfigFeatures::class.java).get().attribution

  private fun findChatSessionFor(messageId: UUID): AgentChatSession? =
      AgentChatSessionService.getInstance(project).findByMessage(messageId)
}
