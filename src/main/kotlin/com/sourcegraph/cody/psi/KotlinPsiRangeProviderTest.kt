package com.sourcegraph.cody.psi

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KotlinPsiRangeProviderTest : BasePlatformTestCase() {
  private lateinit var provider: KotlinPsiRangeProvider

  override fun setUp() {
    super.setUp()
    provider = KotlinPsiRangeProvider()
  }

  private fun setupEditorWithContent(content: String, cursorMarker: String) {
    myFixture.configureByText("Test.kt", content)
    myFixture.editor.caretModel.moveToOffset(content.indexOf(cursorMarker))
  }

  @Suppress("SameParameterValue")
  private fun assertRange(content: String, startMarker: String, endMarker: String?) {
    val range = provider.getDocumentableRange(project, myFixture.editor)
    assertNotNull(range)
    val document = myFixture.editor.document
    assertEquals(content.indexOf(startMarker), range?.toRangeMarker(document)?.startOffset)
    if (endMarker != null) {
      assertEquals(content.indexOf(endMarker), range?.toRangeMarker(document)?.endOffset)
    }
  }

  fun testCursorInsideFunction() {
    val content =
        """
            class TestClass {
                fun testFunction() {
                    // cursor here
                }
            }
        """
            .trimIndent()
    setupEditorWithContent(content, "// cursor here")
    assertRange(content, "fun testFunction", "}")
  }

  fun testCursorInsideClass() {
    val content =
        """
            class TestClass {
                // cursor here
                fun testFunction() {}
            }
        """
            .trimIndent()
    setupEditorWithContent(content, "// cursor here")
    assertRange(content, "class TestClass", "}")
  }

  fun testCursorOutsideDocumentableElement() {
    val content =
        """
            // cursor here
            class TestClass {
                fun testFunction() {}
            }
        """
            .trimIndent()
    setupEditorWithContent(content, "// cursor here")
    val range = provider.getDocumentableRange(project, myFixture.editor)
    assertNull(range)
  }

  fun testEmptyFile() {
    val content = ""
    setupEditorWithContent(content, "")
    val range = provider.getDocumentableRange(project, myFixture.editor)
    assertNull(range)
  }

  fun testMultipleDocumentableElements() {
    val content =
        """
            class TestClass1 {
                fun testFunction1() {}
            }

            class TestClass2 {
                fun testFunction2() {
                    // cursor here
                }
            }
        """
            .trimIndent()
    setupEditorWithContent(content, "// cursor here")
    assertRange(content, "fun testFunction2", "}")
  }
}
