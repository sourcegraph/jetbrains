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
import com.intellij.openapi.fileEditor.FileDocumentManager

class EditShowDiffAction : CompareFileWithEditorAction() {

  override fun isAvailable(e: AnActionEvent): Boolean {
    return true
  }

  override fun getDiffRequestChain(e: AnActionEvent): DiffRequestChain {
    val project = e.project
    val documentBefore = e.dataContext.getData(DOCUMENT_BEFORE_DATA_KEY)
    val documentAfter = e.dataContext.getData(DOCUMENT_AFTER_DATA_KEY)

    val content1 = DiffContentFactory.getInstance().create(project, documentBefore!!)
    content1.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true)
    val content2 = DiffContentFactory.getInstance().create(project, documentAfter!!)

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
    val DOCUMENT_BEFORE_DATA_KEY = DataKey.create<Document?>("document_before")
    val DOCUMENT_AFTER_DATA_KEY = DataKey.create<Document?>("document_after")
  }
}
