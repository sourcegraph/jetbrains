package com.sourcegraph.cody.psi

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class GoPsiRangeProvider : CodyPsiRangeProvider {
  override fun getDocumentableRange(project: Project, editor: Editor): DocumentableRange? {
    if (!isGoPluginInstalled()) {
      // Handle the case where the Go plugin is not installed
      return null
    }

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
    val goFunctionOrMethodDeclarationClass = getGoFunctionOrMethodDeclarationClass() ?: return null
    val goTypeSpecClass = getGoTypeSpecClass() ?: return null

    return PsiTreeUtil.getParentOfType(element, goFunctionOrMethodDeclarationClass, goTypeSpecClass)
  }

  private fun getGoFunctionOrMethodDeclarationClass(): Class<out PsiElement>? {
    return try {
      Class.forName("com.goide.psi.GoFunctionOrMethodDeclaration") as Class<out PsiElement>
    } catch (e: ClassNotFoundException) {
      null
    }
  }

  private fun getGoTypeSpecClass(): Class<out PsiElement>? {
    return try {
      Class.forName("com.goide.psi.GoTypeSpec") as Class<out PsiElement>
    } catch (e: ClassNotFoundException) {
      null
    }
  }

  private fun isGoPluginInstalled(): Boolean {
    val pluginId = PluginId.getId("org.jetbrains.plugins.go")
    return PluginManager.isPluginInstalled(pluginId)
  }
}
