package com.sourcegraph.cody.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinPsiRangeProvider : CodyPsiRangeProvider() {

  @Suppress("UNCHECKED_CAST")
  override fun findDocumentableElement(element: PsiElement): PsiElement? {
    // Explicitly specify the type parameter for PsiTreeUtil.getParentOfType
    // TODO: also allow KtProperty when their parent is a KtClass
    val result =
        PsiTreeUtil.getNonStrictParentOfType(
            element,
            // TODO: Fix the compile-time dependency issue on these Kt* classes.
            // Here's what's happening currently:
            //  - The compiler doesn't let me use KtNamedFunction::class.java as an
            //    argument directly here, because we are missing a dependency on
            //    PsiModifiableCodeBlock from org.jetbrains.kotlin:base-psi.
            //  - As a result, it optimizes out this call and the result is always null.
            // But if you click on KtNamedFunction here, you'll see an option to
            // open the .class file, and the runtime dependency is there; it's the
            // sources that are out of date.
            KtNamedFunction::class.java,
            KtClass::class.java)
    return result
  }
}
