package com.sourcegraph.cody.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresEdt

object CodyPsiRangeProviderFactory {
  private fun createProvider(language: String): CodyPsiRangeProvider? {
    return when (language) {
      "JAVA",
      "java" -> JavaPsiRangeProvider()
      "KOTLIN",
      "kotlin" -> KotlinPsiRangeProvider()
      "GO",
      "go" -> GoPsiRangeProvider()
      else -> null
    }
  }

  @RequiresEdt
  fun getRangeProviderForFile(project: Project, editor: Editor): CodyPsiRangeProvider? {
    return createProvider(
        (PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null)
            .language
            .id)
  }
}
