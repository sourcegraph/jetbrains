package com.sourcegraph.cody.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinPsiRangeProviderTest : BasePlatformTestCase() {
    private lateinit var provider: KotlinPsiRangeProvider

    override fun setUp() {
        super.setUp()
        provider = KotlinPsiRangeProvider()
    }

    fun testCursorInsideFunction() {
        val content = """
            class TestClass {
                fun testFunction() {
                    // cursor here
                }
            }
        """.trimIndent()
        val file = myFixture.configureByText("Test.kt", content)
        myFixture.editor.caretModel.moveToOffset(content.indexOf("// cursor here"))

        val range = provider.getDocumentableRange(project, myFixture.editor)
        assertNotNull(range)
        assertEquals(content.indexOf("fun testFunction"), range?.startOffset)
        assertEquals(content.indexOf("}"), range?.endOffset)
    }

    fun testCursorInsideClass() {
        val content = """
            class TestClass {
                // cursor here
                fun testFunction() {}
            }
        """.trimIndent()
        val file = myFixture.configureByText("Test.kt", content)
        myFixture.editor.caretModel.moveToOffset(content.indexOf("// cursor here"))

        val range = provider.getDocumentableRange(project, myFixture.editor)
        assertNotNull(range)
        assertEquals(content.indexOf("class TestClass"), range?.startOffset)
        assertEquals(content.indexOf("}"), range?.endOffset)
    }

    fun testCursorOutsideDocumentableElement() {
        val content = """
            // cursor here
            class TestClass {
                fun testFunction() {}
            }
        """.trimIndent()
        val file = myFixture.configureByText("Test.kt", content)
        myFixture.editor.caretModel.moveToOffset(content.indexOf("// cursor here"))

        val range = provider.getDocumentableRange(project, myFixture.editor)
        assertNull(range)
    }

    fun testEmptyFile() {
        val content = ""
        val file = myFixture.configureByText("Test.kt", content)
        myFixture.editor.caretModel.moveToOffset(0)

        val range = provider.getDocumentableRange(project, myFixture.editor)
        assertNull(range)
    }

    fun testMultipleDocumentableElements() {
        val content = """
            class TestClass1 {
                fun testFunction1() {}
            }

            class TestClass2 {
                fun testFunction2() {
                    // cursor here
                }
            }
        """.trimIndent()
        val file = myFixture.configureByText("Test.kt", content)
        myFixture.editor.caretModel.moveToOffset(content.indexOf("// cursor here"))

        val range = provider.getDocumentableRange(project, myFixture.editor)
        assertNotNull(range)
        assertEquals(content.indexOf("fun testFunction2"), range?.startOffset)
        assertEquals(content.indexOf("}"), range?.endOffset)
    }
}
