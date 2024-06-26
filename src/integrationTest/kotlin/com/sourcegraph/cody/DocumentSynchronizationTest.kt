package com.sourcegraph.cody

import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.util.DocumentSynchronizationTestFixture
import org.junit.Test

class DocumentSynchronizationTest : DocumentSynchronizationTestFixture() {

  @Test
  fun testInsertCharacter() {
    val beforeContent =
        """
            class Foo {
              console.log(\"hello there@\")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log(\"hello there!\")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      document.insertString(editor.caretModel.offset, "!")
    }
  }
}
