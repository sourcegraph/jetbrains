package com.sourcegraph.find

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.sourcegraph.common.BrowserOpener.openRelativeUrlInBrowser
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

// It seems like the constructor is not called when we use the JSON parser to create instances
// of this class, so
// avoid adding any computation here.
class PreviewContent(
    private val project: Project,
    val receivedDateTime: Date,
    val resultType: String?,
    private val fileName: String?,
    val repoUrl: String,
    val commit: String?,
    val path: String?,
    content: String?,
    symbolName: String?,
    val symbolContainerName: String?,
    val commitMessagePreview: String?,
    private val lineNumber: Int,
    val absoluteOffsetAndLengths: Array<IntArray>,
    private val relativeUrl: String?
) {

  val content by lazy { convertBase64ToString(content) }
  val symbolName by lazy { convertBase64ToString(symbolName) }
  val virtualFile by lazy { SourcegraphVirtualFile(fileName!!, content!!, repoUrl, commit, path) }

  fun openInEditorOrBrowser() {
    if (opensInEditor()) {
      openInEditor()
    } else {
      if (relativeUrl != null) {
        openRelativeUrlInBrowser(project, relativeUrl)
      }
    }
  }

  fun opensInEditor(): Boolean = !fileName.isNullOrEmpty()

  private fun openInEditor() {
    // Open file in editor
    val openFileDescriptor = OpenFileDescriptor(project, virtualFile, 0)
    FileEditorManager.getInstance(project).openTextEditor(openFileDescriptor, true)

    // Suppress code issues
    val file = PsiManager.getInstance(project).findFile(virtualFile)
    if (file != null) {
      DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false)
    }
  }

  override fun equals(other: Any?): Boolean {
    return other is PreviewContent && equals(other as PreviewContent?)
  }

  private fun equals(other: PreviewContent?): Boolean {
    return other != null &&
        fileName == other.fileName &&
        repoUrl == other.repoUrl &&
        path == other.path &&
        content == other.content &&
        symbolName == other.symbolName &&
        symbolContainerName == other.symbolContainerName &&
        commitMessagePreview == other.commitMessagePreview &&
        lineNumber == other.lineNumber &&
        absoluteOffsetAndLengths.contentDeepEquals(other.absoluteOffsetAndLengths) &&
        relativeUrl == other.relativeUrl
  }

  override fun hashCode(): Int {
    var result = project.hashCode()
    result = 31 * result + receivedDateTime.hashCode()
    result = 31 * result + (resultType?.hashCode() ?: 0)
    result = 31 * result + (fileName?.hashCode() ?: 0)
    result = 31 * result + repoUrl.hashCode()
    result = 31 * result + (commit?.hashCode() ?: 0)
    result = 31 * result + (path?.hashCode() ?: 0)
    result = 31 * result + (symbolContainerName?.hashCode() ?: 0)
    result = 31 * result + (commitMessagePreview?.hashCode() ?: 0)
    result = 31 * result + lineNumber
    result = 31 * result + absoluteOffsetAndLengths.contentDeepHashCode()
    result = 31 * result + (relativeUrl?.hashCode() ?: 0)
    return result
  }

  companion object {
    @JvmStatic
    fun fromJson(project: Project, json: JsonObject): PreviewContent {
      val absoluteOffsetAndLengthsSize = json["absoluteOffsetAndLengths"]?.asJsonArray?.size() ?: 0
      val absoluteOffsetAndLengths = Array(absoluteOffsetAndLengthsSize) { IntArray(2) }
      for (i in absoluteOffsetAndLengths.indices) {
        val element = json["absoluteOffsetAndLengths"].asJsonArray[i]
        absoluteOffsetAndLengths[i][0] = element.asJsonArray[0].asInt
        absoluteOffsetAndLengths[i][1] = element.asJsonArray[1].asInt
      }
      return PreviewContent(
          project,
          Date.from(
              Instant.from(DateTimeFormatter.ISO_INSTANT.parse(json["timeAsISOString"].asString))),
          json.getOrNull("resultType")?.asString,
          json.getOrNull("fileName")?.asString,
          json.getOrNull("repoUrl")!!.asString,
          json.getOrNull("commit")?.asString,
          json.getOrNull("path")?.asString,
          json.getOrNull("content")?.asString,
          json.getOrNull("symbolName")?.asString,
          json.getOrNull("symbolContainerName")?.asString,
          json.getOrNull("commitMessagePreview")?.asString,
          json.getOrNull("lineNumber")?.asInt ?: -1,
          absoluteOffsetAndLengths,
          json.getOrNull("relativeUrl")?.asString,
      )
    }

    private fun JsonObject.getOrNull(key: String): JsonElement? =
        this[key]?.takeUnless { it.isJsonNull }

    private fun convertBase64ToString(base64String: String?): String? {
      if (base64String == null) return null
      val decodedBytes = Base64.getDecoder().decode(base64String)
      return String(decodedBytes, StandardCharsets.UTF_8)
    }
  }
}
