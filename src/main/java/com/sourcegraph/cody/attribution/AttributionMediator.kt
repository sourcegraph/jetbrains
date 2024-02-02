package com.sourcegraph.cody.attribution

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CurrentConfigFeatures
import com.sourcegraph.cody.chat.AgentChatSession
import com.sourcegraph.cody.chat.AgentChatSessionService
import com.sourcegraph.cody.chat.ui.CodeEditorPart
import java.util.*

/**
 * {@link AttributionMediator} is used to mediate between
 * chat session triggering an attribution search once
 * code snippet was finished printing, and then updating
 * the UI on the code snippet accordingly.
 */
@Service(Service.Level.PROJECT)
class AttributionMediator(private val project: Project) {
  companion object {
    fun instance(project: Project): AttributionMediator =
      project.getService(AttributionMediator::class.java)
  }

  fun onSnippetFinished(editor: CodeEditorPart, messageId: UUID) {
    if (attributionEnabled()) {
      findChatSessionFor(messageId)?.snippetAttribution(editor.text.get(), callbackInUiThread(editor.attributionListener))
    }
  }

  private fun attributionEnabled(): Boolean = project.getService(CurrentConfigFeatures::class.java)
    .get().attribution

  private fun findChatSessionFor(messageId: UUID): AgentChatSession? =
    AgentChatSessionService.getInstance(project).findByMessage(messageId)

  private fun callbackInUiThread(listener: AttributionListener): AttributionListener =
    AttributionListener { response ->
      ApplicationManager.getApplication().invokeLater {
        listener.updateAttribution(response)
      }
    }
}