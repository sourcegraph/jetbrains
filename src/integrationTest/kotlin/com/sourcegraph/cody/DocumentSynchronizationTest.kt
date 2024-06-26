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
              console.log("hello there@")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("hello there!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      document.insertString(editor.caretModel.offset, "!")
    }
  }

  @Test
  fun testDeleteCharacter() {
    val beforeContent =
        """
            class Foo {
              console.log("hello there^!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("hello there")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      document.deleteString(offset, offset + 1)
    }
  }

  @Test
  fun testDeleteRange() {
    val beforeContent =
        """
            class Foo {
              ^console.log("hello there!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              ("hello there!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      document.deleteString(offset, offset + "console.log".length)
    }
  }

  @Test
  fun testReplaceRangeAtomically() {
    val beforeContent =
        """
            class Foo {
              ^System.out.println("hello there!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("hello there!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      document.replaceString(offset, offset + "System.out.println".length, "console.log")
    }
  }

  @Test
  fun testReplaceRangeNonAtomically() {
    val beforeContent =
        """
            class Foo {
              ^System.out.println("Cześć!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("Cześć!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      document.deleteString(offset, "System.".length)
      document.deleteString(offset, "out.".length)
      document.deleteString(offset, "println".length)
      document.insertString(offset, "console.log")
    }
  }

  @Test
  fun testInsertWithNewlines() {
    val beforeContent =
        """
            class Foo {
              console.log("hello there!")@
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("hello there!")
              console.log("this is a test")
              console.log("hello hello")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      document.insertString(offset, "\n  console.log(\"this is a test\")")
      document.insertString(offset + 29, "\n  console.log(\"hello hello\")")
    }
  }

  @Test
  fun testEraseDocument() {
    val beforeContent =
        """
            class Foo {
              ^console.log("hello there!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent = ""

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      document.deleteString(0, document.textLength)
    }
  }

  @Test
  fun testAppendToEndOfDocument() {
    val beforeContent =
        """
            class Foo {
              console.log("hello there!")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("hello there!")
            }
            // antidisestablishmentarianism
            // pneumonoultramicroscopicsilicovolcanoconiosis
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = document.textLength
      document.insertString(offset, "\n// antidisestablishmentarianism")
      document.insertString(offset + 34, "\n// pneumonoultramicroscopicsilicovolcanoconiosis")
    }
  }

  @Test
  fun testDeleteRangesWithNewlines() {
    val beforeContent =
        """
            class Foo {
              console.log("item 1")@
              console.log("item 2")
              console.log("item 3")
              console.log("item 4")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("item 1")
              console.log("item 4")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      val endOffset = document.getLineEndOffset(document.getLineNumber(offset) + 2)
      document.deleteString(offset, endOffset)
    }
  }

  @Test
  fun testInsertEmojis() {
    val beforeContent =
        """
            class Foo {
              console.log("hello there^")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            class Foo {
              console.log("hello there!🎉🎂
              🥳🎈")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      document.insertString(offset, "!🎉🎂\n🥳🎈")
    }
  }

  @Test
  fun testMultipleEdits() {
    val beforeContent =
        """
            class Foo {
              console.log("hello there")
            }
        """
            .trimIndent()
            .removePrefix("\n")

    val expectedContent =
        """
            import com.foo.Bar;

            class Foo {
              // no comment
              console.log("hello there");
            }
            // end class Foo
        """
            .trimIndent()
            .removePrefix("\n")

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      document.insertString(0, "import com.foo.Bar;\n\n")
      val offset = document.getLineEndOffset(2)
      document.insertString(offset, "\n  // no comment")
      document.insertString(document.textLength - 1, ";")
      document.insertString(document.textLength, "\n// end class Foo")
    }
  }
}
