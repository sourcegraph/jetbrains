package com.sourcegraph.cody.edit.actions.lenses

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol_extensions.toOffset
import com.sourcegraph.cody.agent.protocol_generated.EditTask_GetTaskDetailsParams
import com.sourcegraph.common.ShowDocumentDiffAction

class EditShowDiffAction :
    LensEditAction({ project, event, editor, taskId ->
      CodyAgentService.withAgent(project) { agent ->
        WriteCommandAction.runWriteCommandAction<Unit>(project) {
          val editTask =
              agent.server.getEditTaskDetails(EditTask_GetTaskDetailsParams(taskId)).get()
          if (editTask != null) {
            val documentAfter = editor.document
            val documentBefore = EditorFactory.getInstance().createDocument(documentAfter.text)
            documentBefore.replaceString(
                editTask.selectionRange.start.toOffset(documentBefore),
                editTask.selectionRange.end.toOffset(documentBefore),
                editTask.originalText ?: "")
            ShowDocumentDiffAction(documentBefore, documentAfter).actionPerformed(event)
          }
        }
      }
    }) {
  companion object {
    const val ID = "cody.fixup.codelens.diff"
  }
}
