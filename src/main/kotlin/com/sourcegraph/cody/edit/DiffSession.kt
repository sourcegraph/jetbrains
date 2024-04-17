package com.sourcegraph.cody.edit

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project

class DiffSession(
    project: Project,
    document: Document,
    performedActions: MutableList<FixupUndoableAction>
) : BaseFixupSession(document) {
  private val logger = Logger.getInstance(DiffSession::class.java)

  init {
    performedActions
        .mapNotNull { it.afterMarker }
        .map { createMarker(it.startOffset, it.endOffset) }
    val sortedEdits =
        performedActions.zip(rangeMarkers).sortedByDescending { it.second.startOffset }

    WriteCommandAction.runWriteCommandAction(project) {
      for ((fixupAction, marker) in sortedEdits) {
        val tmpAfterMarker = fixupAction.afterMarker ?: break

        when (fixupAction.edit.type) {
          "replace",
          "delete" -> {
            ReplaceUndoableAction(project, session = this, fixupAction.edit, marker)
                .apply {
                  afterMarker = createMarker(tmpAfterMarker.startOffset, tmpAfterMarker.endOffset)
                  originalText = fixupAction.originalText
                }
                .undo()
          }
          "insert" -> {
            InsertUndoableAction(project, session = this, fixupAction.edit, marker)
                .apply {
                  afterMarker = createMarker(tmpAfterMarker.startOffset, tmpAfterMarker.endOffset)
                  originalText = fixupAction.originalText
                }
                .undo()
          }
          else -> logger.warn("Unknown edit type: ${fixupAction.edit.type}")
        }
      }
    }
  }
}
