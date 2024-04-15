package com.sourcegraph.cody.edit

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.actions.BlankDiffWindowUtil.createBlankDiffRequestChain
import com.intellij.diff.actions.CompareFileWithEditorAction
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.sourcegraph.cody.agent.protocol.Range

class EditShowDiffAction : CompareFileWithEditorAction() {

  override fun isAvailable(e: AnActionEvent): Boolean {
    e.dataContext.getData(DOCUMENT_BEFORE_DATA_KEY) ?: return false
    e.dataContext.getData(SELECTION_RANGE_DATA_KEY) ?: return false
    return true
  }

  override fun getDiffRequestChain(e: AnActionEvent): DiffRequestChain {
    val project = e.project
    val selectionRange = e.dataContext.getData(SELECTION_RANGE_DATA_KEY)!!
    val documentBefore = e.dataContext.getData(DOCUMENT_BEFORE_DATA_KEY)!!
    val documentAfter = e.dataContext.getData(EDITOR_DATA_KEY)!!.document
    val diffDocument: Document = EditorFactory.getInstance().createDocument(documentAfter.text)

    val start = documentBefore.getLineEndOffset(selectionRange.start.line - 1)
    val end = documentBefore.getLineEndOffset(selectionRange.end.line)
    val selectionText = documentBefore.text.substring(start, end)
    diffDocument.text.replaceRange(start, end, selectionText)

    val content1 = DiffContentFactory.getInstance().create(project, documentBefore)
    content1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true)
    val content2 = DiffContentFactory.getInstance().create(project, documentAfter)

    val editorFile = FileDocumentManager.getInstance().getFile(documentAfter)
    val editorContentTitle =
        when {
          editorFile == null -> "Editor"
          else -> DiffRequestFactory.getInstance().getContentTitle(editorFile)
        }

    val chain = createBlankDiffRequestChain(content1, content2, baseContent = null)
    chain.windowTitle =
        when {
          editorFile == null -> "Cody Diff"
          else -> "Cody Diff: $editorContentTitle"
        }
    chain.title1 = "Before Cody Inline Edit"
    chain.title2 = editorContentTitle

    return chain
  }

  companion object {
    val DOCUMENT_BEFORE_DATA_KEY = DataKey.create<Document>("document_before")
    val SELECTION_RANGE_DATA_KEY = DataKey.create<Range>("selection_range")
    val EDITOR_DATA_KEY = DataKey.create<Editor>("editor")
  }
}
