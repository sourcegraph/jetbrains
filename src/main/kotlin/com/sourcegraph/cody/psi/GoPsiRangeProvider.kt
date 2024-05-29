package com.sourcegraph.cody.psi

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.sourcegraph.cody.agent.protocol.Range

class GoPsiRangeProvider : CodyPsiRangeProvider() {

  override fun getDocumentableRange(project: Project, editor: Editor): Range? {
    return if (!isGoPluginInstalled()) {
      null
    } else {
      super.getDocumentableRange(project, editor)
    }
  }

  override fun findDocumentableElement(element: PsiElement): PsiElement? {
    val goFunctionOrMethodDeclarationClass = getGoFunctionOrMethodDeclarationClass() ?: return null
    val goTypeSpecClass = getGoTypeSpecClass() ?: return null

    return PsiTreeUtil.getParentOfType(element, goFunctionOrMethodDeclarationClass, goTypeSpecClass)
  }

  @Suppress("UNCHECKED_CAST")
  private fun getGoFunctionOrMethodDeclarationClass(): Class<out PsiElement>? {
    return try {
      Class.forName("com.goide.psi.GoFunctionOrMethodDeclaration") as Class<out PsiElement>
    } catch (e: ClassNotFoundException) {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
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
