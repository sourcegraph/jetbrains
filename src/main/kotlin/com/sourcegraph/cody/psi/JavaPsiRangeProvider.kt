package com.sourcegraph.cody.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

class JavaPsiRangeProvider : CodyPsiRangeProvider {
    override fun getDocumentableRange(project: Project, editor: Editor): DocumentableRange? {
        val psiFile = getPsiFile(project, editor) ?: return null
        val caretOffset = editor.caretModel.offset
        val element = psiFile.findElementAt(caretOffset) ?: return null

        val documentableElement = findDocumentableElement(element) ?: return null
        return DocumentableRange(documentableElement.textRange.startOffset, documentableElement.textRange.endOffset)
    }

    private fun getPsiFile(project: Project, editor: Editor): PsiFile? {
        val document = editor.document
        return PsiDocumentManager.getInstance(project).getPsiFile(document)
    }

    private fun findDocumentableElement(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, PsiClass::class.java)
    }
}
