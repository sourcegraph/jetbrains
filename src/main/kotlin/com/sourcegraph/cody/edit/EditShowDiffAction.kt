package com.sourcegraph.cody.edit

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.actions.BlankDiffWindowUtil.REMEMBER_CONTENT_KEY
import com.intellij.diff.actions.BlankDiffWindowUtil.createBlankDiffRequestChain
import com.intellij.diff.actions.CompareFileWithEditorAction
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.Side
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.sourcegraph.cody.agent.protocol.Range

class EditShowDiffAction : CompareFileWithEditorAction() {

  override fun isAvailable(e: AnActionEvent): Boolean {
    return true
  }

  override fun getDiffRequestChain(e: AnActionEvent): DiffRequestChain {
    val project = e.project
    val editor = e.dataContext.getData(EDITOR_DATA_KEY)
    val selectionRange = e.getData(SELECTION_RANGE_DATA_KEY)

    val content2 = createContent(project, editor!!, selectionRange!!)
    val content1 = DiffContentFactory.getInstance().createClipboardContent(project, content2)
    content1.putUserData<Boolean>(REMEMBER_CONTENT_KEY, true) // todo: remove?

    val editorFile = FileDocumentManager.getInstance().getFile(editor.document)
    val editorContentTitle =
        if (editorFile != null) DiffRequestFactory.getInstance().getContentTitle(editorFile)
        else "Editor"

    val chain = createBlankDiffRequestChain(content1, content2, null)
    val windowTitle = if (editorFile != null) "cośtam coś tam" else "inne cosie"
    chain.windowTitle = windowTitle
    chain.title1 = "Before Cody Inline Edit"
    chain.title2 = editorContentTitle

    val currentLine = editor.caretModel.logicalPosition.line
    chain.putRequestUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentLine))

    return chain
  }

  companion object {

    private fun createContent(
        project: Project?,
        editor: Editor,
        selectedContent: Range
    ): DocumentContent {
      var content = DiffContentFactory.getInstance().create(project, editor.document)

      val selectionModel = editor.selectionModel
      val range = TextRange(selectedContent.start.line, selectedContent.end.line)
      content = DiffContentFactory.getInstance().createFragment(project, content, range)

      if (editor.isViewer) content.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true)

      return content
    }

    val EDITOR_DATA_KEY = DataKey.create<Editor?>("editor")
    val SELECTION_RANGE_DATA_KEY = DataKey.create<Range?>("selectionRange")
  }
}
