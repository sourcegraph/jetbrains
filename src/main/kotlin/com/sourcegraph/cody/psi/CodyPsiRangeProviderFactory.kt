package com.sourcegraph.cody.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

class CodyPsiRangeProviderFactory {
    fun getProvider(project: Project, editor: Editor): CodyPsiRangeProvider? {
        val psiFile = getPsiFile(project, editor) ?: return null
        val language = psiFile.language.id

        return when (language) {
            "JAVA" -> JavaPsiRangeProvider()
            "Kotlin" -> KotlinPsiRangeProvider()
            "Go" -> GoPsiRangeProvider()
            else -> null
        }
    }

    private fun getPsiFile(project: Project, editor: Editor): PsiFile? {
        val document = editor.document
        return PsiDocumentManager.getInstance(project).getPsiFile(document)
    }
}
