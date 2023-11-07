package com.sourcegraph.telemetry

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.PluginUtil
import com.sourcegraph.cody.agent.CodyAgent.Companion.withServer
import com.sourcegraph.cody.agent.CodyAgentServer
import com.sourcegraph.cody.agent.protocol.CompletionEvent
import com.sourcegraph.cody.agent.protocol.Event
import com.sourcegraph.cody.config.CodyApplicationSettings
import com.sourcegraph.cody.config.SourcegraphServerPath
import com.sourcegraph.config.ConfigUtil.getPluginVersion
import com.sourcegraph.config.ConfigUtil.getServerPath
import java.util.concurrent.CompletableFuture

object GraphQlLogger {
  private val gson = GsonBuilder().serializeNulls().create()

  @JvmStatic
  fun logInstallEvent(project: Project): CompletableFuture<Boolean> {
    val anonymousUserId = CodyApplicationSettings.instance.anonymousUserId
    if (anonymousUserId != null && !project.isDisposed) {
      val event = createEvent(getServerPath(project), "CodyInstalled", JsonObject())
      return logEvent(project, event)
    }
    return CompletableFuture.completedFuture(false)
  }

  @JvmStatic
  fun logUninstallEvent(project: Project) {
    val anonymousUserId = CodyApplicationSettings.instance.anonymousUserId
    if (anonymousUserId != null) {
      val event = createEvent(getServerPath(project), "CodyUninstalled", JsonObject())
      logEvent(project, event)
    }
  }

  @JvmStatic
  fun logAutocompleteSuggestedEvent(
      project: Project,
      latencyMs: Long,
      displayDurationMs: Long,
      params: CompletionEvent.Params?
  ) {
    val eventName = "CodyJetBrainsPlugin:completion:suggested"
    val eventParameters =
        JsonObject().apply {
          addProperty("latency", latencyMs)
          addProperty("displayDuration", displayDurationMs)
          addProperty("isAnyKnownPluginEnabled", PluginUtil.isAnyKnownPluginEnabled())
        }
    val updatedEventParameters = addCompletionEventParams(eventParameters, params)
    logEvent(project, createEvent(getServerPath(project), eventName, updatedEventParameters))
  }

  @JvmStatic
  fun logAutocompleteAcceptedEvent(project: Project, params: CompletionEvent.Params?) {
    val eventName = "CodyJetBrainsPlugin:completion:accepted"
    val eventParameters = addCompletionEventParams(JsonObject(), params)
    logEvent(project, createEvent(getServerPath(project), eventName, eventParameters))
  }

  private fun addCompletionEventParams(
      eventParameters: JsonObject,
      params: CompletionEvent.Params?
  ): JsonObject {
    return eventParameters.deepCopy().apply {
      if (params != null) {
        if (params.contextSummary != null) {
          add("contextSummary", gson.toJsonTree(params.contextSummary))
        }
        addProperty("id", params.id)
        addProperty("languageId", params.languageId)
        addProperty("source", params.source)
        addProperty("charCount", params.charCount)
        addProperty("lineCount", params.lineCount)
        addProperty("multilineMode", params.multiline)
        addProperty("providerIdentifier", params.providerIdentifier)
      }
    }
  }

  @JvmStatic
  fun logCodyEvent(project: Project, componentName: String, action: String) {
    val eventName = "CodyJetBrainsPlugin:$componentName:$action"
    logEvent(project, createEvent(getServerPath(project), eventName, JsonObject()))
  }

  private fun createEvent(
      sourcegraphServerPath: SourcegraphServerPath,
      eventName: String,
      eventParameters: JsonObject
  ): Event {
    val updatedEventParameters = addGlobalEventParameters(eventParameters, sourcegraphServerPath)
    val anonymousUserId = CodyApplicationSettings.instance.anonymousUserId
    return Event(eventName, anonymousUserId ?: "", "", updatedEventParameters)
  }

  private fun addGlobalEventParameters(
      eventParameters: JsonObject,
      sourcegraphServerPath: SourcegraphServerPath
  ): JsonObject {
    // project specific properties
    return eventParameters.deepCopy().apply {
      addProperty("serverEndpoint", sourcegraphServerPath.url)
      // Extension specific properties
      val extensionDetails =
          JsonObject().apply {
            addProperty("ide", "JetBrains")
            addProperty("ideExtensionType", "Cody")
            addProperty("version", getPluginVersion())
          }
      add("extensionDetails", extensionDetails)
    }
  }

  // This could be exposed later (as public), but currently, we don't use it externally.
  private fun logEvent(project: Project, event: Event): CompletableFuture<Boolean> {
    return withServer(project) { server: CodyAgentServer? -> server!!.logEvent(event) }
        .thenApply { true }
        .exceptionally { false }
  }
}
