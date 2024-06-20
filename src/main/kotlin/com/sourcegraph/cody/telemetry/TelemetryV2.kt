package com.sourcegraph.cody.telemetry

import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.TelemetryEvent
import com.sourcegraph.cody.agent.protocol.TelemetryEventParameters

class TelemetryV2 {
  companion object {
    fun sendTelemetryEvent(
        project: Project,
        feature: String,
        action: String,
        parameters: TelemetryEventParameters? = null
    ) {
      CodyAgentService.withAgent(project) { agent ->
        agent.server.recordEvent(
            TelemetryEvent(feature = "cody.$feature", action = action, parameters = parameters))
      }
    }

    fun sendCodeGenerationEvent(project: Project, feature: String, action: String, code: String) {
      val op =
          if (action.startsWith("copy")) "copy"
          else if (action.startsWith("insert")) "insert" else "save"

      val metadata =
          mapOf("lineCount" to code.lines().count().toLong(), "charCount" to code.length.toLong())

      val privateMetadata = mapOf("op" to op, "source" to "chat")

      sendTelemetryEvent(
          project = project,
          feature = feature,
          action = action,
          parameters =
              TelemetryEventParameters(metadata = metadata, privateMetadata = privateMetadata))
    }
  }
}
