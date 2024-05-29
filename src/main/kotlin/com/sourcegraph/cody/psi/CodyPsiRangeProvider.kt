package com.sourcegraph.cody.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore.getPsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.protocol.Position
import com.sourcegraph.cody.agent.protocol.Range

abstract class CodyPsiRangeProvider {

  @RequiresEdt abstract fun findDocumentableElement(element: PsiElement): PsiElement?

  @RequiresEdt
  open fun getDocumentableRange(project: Project, editor: Editor): Range? {
    val document = editor.document
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null
    val psiFile = getPsiFile(project, file)
    val caretOffset = editor.caretModel.offset
    val element = psiFile.findElementAt(caretOffset) ?: return null

    val documentableElement = findDocumentableElement(element) ?: return null
    return Range(
        Position.fromOffset(document, documentableElement.textRange.startOffset),
        Position.fromOffset(document, documentableElement.textRange.endOffset))
  }
}
