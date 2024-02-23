package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.AnActionLink
import com.sourcegraph.cody.agent.protocol.ContextFileFile
import java.awt.Color
import java.awt.Graphics

class ContextFileActionLink(
    project: Project,
    contextFileFile: ContextFileFile,
    anAction: AnAction
) : AnActionLink("", anAction) {
  private val localFileBackground = JBColor(Color(182, 210, 242), Color(56, 85, 112))
  private val isReferringToLocalFile = contextFileFile.repoName == null

  init {
    text = contextFileFile.getLinkActionText(project.basePath)
    toolTipText = contextFileFile.uri.path
  }

  override fun paintComponent(g: Graphics) {
    if (isReferringToLocalFile) {
      g.color = localFileBackground

      val fm = g.fontMetrics
      val rect = fm.getStringBounds(text, g)
      val textWidth = rect.width.toInt()
      g.fillRect(0, 0, textWidth, height)
    }
    super.paintComponent(g)
  }

  private fun ContextFileFile.getLinkActionText(projectPath: String?): String {
    val theRange = if (repoName == null) range?.intellijRange() else range?.toSearchRange()
    val path =
        if (repoName == null) {
          "@${uri.path.removePrefix(projectPath ?: "")}"
        } else {
          val repoCommitFile = uri.path.split("@", "/-/blob/")
          if (repoCommitFile.size == 3) {
            val repo = repoCommitFile[0].split("/").lastOrNull()
            "$repo ${repoCommitFile[2]}"
          } else uri.path
        }

    return buildString {
      append(path)
      if (theRange != null) {
        if (theRange.first < theRange.second) {
          append(":${theRange.first}-${theRange.second}")
        } else {
          append(":${theRange.first}")
        }
      }
    }
  }
}
