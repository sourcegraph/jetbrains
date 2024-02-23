package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.AnActionLink
import com.sourcegraph.cody.agent.protocol.ContextFileFile

class ContextFileActionLink(
    project: Project,
    contextFileFile: ContextFileFile,
    anAction: AnAction
) : AnActionLink("", anAction) {
  init {
    text = contextFileFile.getLinkActionText(project.basePath)
    toolTipText = contextFileFile.uri.path
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
