package com.sourcegraph.cody

import com.intellij.openapi.editor.Editor
import com.sourcegraph.cody.util.DocumentSynchronizationTestFixture
import org.junit.Test

class DocumentSynchronizationTest : DocumentSynchronizationTestFixture() {

  // TODO: More tests that would be useful:
  //  - Test bulk updates with the com.intellij.util.DocumentUtil#executeInBulk method.
  //  - Changes to multiple files.
  //  - (your test idea here)

  @Test
  fun testInsertCharacter() {
    val beforeContent =
        """
             class Foo {
               console.log("hello there^")
             }
         """

    val expectedContent =
        """
             class Foo {
               console.log("hello there!")
             }
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      editor.document.insertString(editor.caretModel.offset, "!")
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

    val expectedContent =
        """
             class Foo {
               console.log("hello there")
             }
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val offset = editor.caretModel.offset
      editor.document.deleteString(offset, offset + 1)
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

    val expectedContent =
        """
             class Foo {
               ("hello there!")
             }
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val offset = editor.caretModel.offset
      editor.document.deleteString(offset, offset + "console.log".length)
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

    val expectedContent =
        """
             class Foo {
               console.log("hello there!")
             }
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val offset = editor.caretModel.offset
      editor.document.replaceString(offset, offset + "System.out.println".length, "console.log")
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

    val expectedContent =
        """
             class Foo {
               console.log("Cześć!")
             }
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      document.deleteString(offset, offset + "System.".length)
      document.deleteString(offset, offset + "out.".length)
      document.deleteString(offset, offset + "println".length)
      document.insertString(offset, "console.log")
    }
  }

  @Test
  fun testInsertWithNewlines() {
    val beforeContent =
        """
             class Foo {
               console.log("hello there!")^
             }
         """

    val expectedContent =
        """
             class Foo {
               console.log("hello there!")
               console.log("this is a test")
               console.log("hello hello")
             }
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      val firstInsertion = "\n  console.log(\"this is a test\")"
      val secondInsertion = "\n  console.log(\"hello hello\")"
      document.insertString(offset, firstInsertion)
      document.insertString(offset + firstInsertion.length, secondInsertion)
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

    val expectedContent = ""

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      editor.document.setText("")
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

    val expectedContent =
        """
             class Foo {
               console.log("hello there!")
             }
             // antidisestablishmentarianism
             // pneumonoultramicroscopicsilicovolcanoconiosis
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = document.textLength
      val firstString = "\n// antidisestablishmentarianism"
      val secondString = "\n// pneumonoultramicroscopicsilicovolcanoconiosis"
      document.insertString(offset, firstString)
      document.insertString(offset + firstString.length, secondString)
    }
  }

  @Test
  fun testDeleteRangesWithNewlines() {
    val beforeContent =
        """
             class Foo {
               console.log("item 1")^
               console.log("item 2")
               console.log("item 3")
               console.log("item 4")
             }
         """

    val expectedContent =
        """
             class Foo {
               console.log("item 1")
               console.log("item 4")
             }
         """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val offset = editor.caretModel.offset
      val startLine = document.getLineNumber(offset) // line 1
      val startOffset = document.getLineStartOffset(startLine + 1)
      val endOffset = document.getLineStartOffset(startLine + 3)
      document.deleteString(startOffset, endOffset)
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

    val expectedContent =
        """
            class Foo {
              console.log("hello there!🎉🎂
              🥳🎈")
            }
        """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      editor.document.insertString(editor.caretModel.offset, "!🎉🎂\n  🥳🎈")
    }
  }

  @Test
  fun testMultipleDisjointEdits() {
    val beforeContent =
        """
            class Foo {
              console.log("hello there")
            }
        """

    val expectedContent =
        """
            import com.foo.Bar;

            class Foo {
              // no comment
              console.log("hello there");
            }
            // end class Foo
        """

    runDocumentSynchronizationTest(beforeContent, expectedContent) { editor: Editor ->
      val document = editor.document
      val importStatement = "import com.foo.Bar;\n\n"
      val comment = "\n  // no comment"
      val endClassComment = "\n// end class Foo"

      document.insertString(0, importStatement)
      val classLine = document.getLineNumber(document.text.indexOf("class Foo {"))
      val offset = document.getLineEndOffset(classLine)
      document.insertString(offset, comment)
      document.insertString(document.getLineEndOffset(classLine + 2), ";")
      document.insertString(document.textLength, endClassComment)
    }
  }
}
