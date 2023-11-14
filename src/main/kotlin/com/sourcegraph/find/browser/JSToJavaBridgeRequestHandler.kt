package com.sourcegraph.find.browser

import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefJSQuery
import com.sourcegraph.cody.config.CodyProjectSettings.Companion.getInstance
import com.sourcegraph.config.ConfigUtil.getConfigAsJson
import com.sourcegraph.config.ThemeUtil
import com.sourcegraph.find.FindPopupPanel
import com.sourcegraph.find.FindService
import com.sourcegraph.find.PreviewContent
import com.sourcegraph.find.PreviewContent.Companion.fromJson
import com.sourcegraph.find.Search
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class JSToJavaBridgeRequestHandler(
    val project: Project,
    private val findPopupPanel: FindPopupPanel,
    private val findService: FindService
) {
  fun handle(request: JsonObject): JBCefJSQuery.Response {
    val action = request["action"].asString
    val arguments: JsonObject
    val previewContent: PreviewContent
    val codyProjectSettings = getInstance(project)
    try {
      when (action) {
        "getConfig" -> return createSuccessResponse(getConfigAsJson(project))
        "getTheme" -> {
          val currentThemeAsJson = ThemeUtil.getCurrentThemeAsJson()
          return createSuccessResponse(currentThemeAsJson)
        }
        "indicateSearchError" -> {
          arguments = request["arguments"].asJsonObject
          // This must run on EDT (Event Dispatch Thread) because it changes the UI.
          ApplicationManager.getApplication().invokeLater {
            findPopupPanel.indicateSearchError(
                arguments["errorMessage"].asString,
                Date.from(
                    Instant.from(
                        DateTimeFormatter.ISO_INSTANT.parse(
                            arguments["timeAsISOString"].asString))))
          }
          return createSuccessResponse(null)
        }
        "saveLastSearch" -> {
          arguments = request["arguments"].asJsonObject
          val query = arguments["query"].asString
          val caseSensitive = arguments["caseSensitive"].asBoolean
          val patternType = arguments["patternType"].asString
          val selectedSearchContextSpec = arguments["selectedSearchContextSpec"].asString
          codyProjectSettings.setLastSearch(
              Search(query, caseSensitive, patternType, selectedSearchContextSpec))
          return createSuccessResponse(JsonObject())
        }
        "loadLastSearch" -> {
          val lastSearch = codyProjectSettings.getLastSearch() ?: return createSuccessResponse(null)
          val lastSearchAsJson =
              JsonObject().apply {
                addProperty("query", lastSearch.query)
                addProperty("caseSensitive", lastSearch.isCaseSensitive)
                addProperty("patternType", lastSearch.patternType)
                addProperty("selectedSearchContextSpec", lastSearch.selectedSearchContextSpec)
              }
          return createSuccessResponse(lastSearchAsJson)
        }
        "previewLoading" -> {
          arguments = request["arguments"].asJsonObject
          // Wait a bit to avoid flickering in case of a fast network
          Thread {
                try {
                  Thread.sleep(300)
                } catch (ignored: InterruptedException) {}
                // This must run on EDT (Event Dispatch Thread) because it changes the UI.
                ApplicationManager.getApplication().invokeLater {
                  findPopupPanel.indicateLoadingIfInTime(
                      Date.from(
                          Instant.from(
                              DateTimeFormatter.ISO_INSTANT.parse(
                                  arguments["timeAsISOString"].asString))))
                }
              }
              .start()
          return createSuccessResponse(null)
        }
        "preview" -> {
          arguments = request["arguments"].asJsonObject
          previewContent = fromJson(project, arguments)
          // This must run on EDT (Event Dispatch Thread) because it changes the UI.
          ApplicationManager.getApplication().invokeLater {
            findPopupPanel.setPreviewContentIfInTime(previewContent)
          }
          return createSuccessResponse(null)
        }
        "clearPreview" -> {
          arguments = request["arguments"].asJsonObject
          // This must run on EDT (Event Dispatch Thread) because it changes the UI.
          ApplicationManager.getApplication().invokeLater {
            findPopupPanel.clearPreviewContentIfInTime(
                Date.from(
                    Instant.from(
                        DateTimeFormatter.ISO_INSTANT.parse(
                            arguments["timeAsISOString"].asString))))
          }
          return createSuccessResponse(null)
        }
        "open" -> {
          arguments = request["arguments"].asJsonObject
          try {
            previewContent = fromJson(project, arguments)
          } catch (e: Exception) {
            return createErrorResponse("Parsing error while opening link: ${e.javaClass.name}: ${e.message}",
                convertStackTraceToString(e))
          }

          // This must run on EDT (Event Dispatch Thread) because it changes the UI.
          ApplicationManager.getApplication().invokeLater {
            try {
              previewContent.openInEditorOrBrowser()
            } catch (e: Exception) {
              val logger: Logger = Logger.getInstance(JSToJavaBridgeRequestHandler::class.java)
              logger.warn("Error while opening link.", e)
            }
          }
          return createSuccessResponse(null)
        }
        "indicateFinishedLoading" -> {
          arguments = request["arguments"].asJsonObject
          // This must run on EDT (Event Dispatch Thread) because it changes the UI.
          ApplicationManager.getApplication().invokeLater {
            findPopupPanel.indicateAuthenticationStatus(
                arguments["wasServerAccessSuccessful"].asBoolean,
                arguments["wasAuthenticationSuccessful"].asBoolean)
          }
          return createSuccessResponse(null)
        }
        "windowClose" -> {
          // This must run on EDT (Event Dispatch Thread) because it changes the UI.
          ApplicationManager.getApplication().invokeLater { findService.hidePopup() }
          return createSuccessResponse(null)
        }
        else -> return createErrorResponse("Unknown action: '$action'.", "No stack trace")
      }
    } catch (e: Exception) {
      return createErrorResponse("$action: ${e.javaClass.name}: ${e.message}", convertStackTraceToString(e))
    }
  }

  fun handleInvalidRequest(e: Exception): JBCefJSQuery.Response {
    return createErrorResponse(
        "Invalid JSON passed to bridge. The error is: ${e.javaClass}: ${e.message}",
        convertStackTraceToString(e))
  }

  private fun createSuccessResponse(result: JsonObject?): JBCefJSQuery.Response {
    return JBCefJSQuery.Response(result?.toString() ?: "null")
  }

  private fun createErrorResponse(errorMessage: String, stackTrace: String): JBCefJSQuery.Response {
    return JBCefJSQuery.Response(null, 0, errorMessage + "\n" + stackTrace)
  }

  private fun convertStackTraceToString(e: Exception): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    e.printStackTrace(pw)
    return sw.toString()
  }
}
