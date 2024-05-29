package com.sourcegraph.cody.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

class JavaPsiRangeProvider : CodyPsiRangeProvider() {

  override fun findDocumentableElement(element: PsiElement): PsiElement? {
    return PsiTreeUtil.getNonStrictParentOfType(
        element, PsiMethod::class.java, PsiClass::class.java)
  }
}
