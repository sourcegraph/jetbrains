package com.sourcegraph.utils

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.suggested.endOffset

class CodyFormatter {
  companion object {
    /**
     * Formatting used to format inlay text inserted by Cody, based on the surrounding code style in
     * the document.
     */
    fun formatStringBasedOnDocument(
        originalText: String,
        project: Project,
        document: Document,
        offset: Int
    ): String {

      val appendedString =
          document.text.substring(0, offset) + originalText + document.text.substring(offset)

      val file = FileDocumentManager.getInstance().getFile(document) ?: return originalText
      val psiFile =
          PsiFileFactory.getInstance(project)
              .createFileFromText("TEMP", file.fileType, appendedString)

      fun endOffset() = offset + psiFile.endOffset - document.textLength
      val codeStyleManager = CodeStyleManager.getInstance(project)
      codeStyleManager.reformatText(psiFile, offset + 1, endOffset())

      return psiFile.text.substring(offset, endOffset())
    }
  }
}
