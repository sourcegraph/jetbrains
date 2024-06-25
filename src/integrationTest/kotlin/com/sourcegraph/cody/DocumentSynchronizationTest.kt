package com.sourcegraph.cody

import com.intellij.openapi.command.WriteCommandAction
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.GetDocumentsParams
import com.sourcegraph.cody.util.CodyIntegrationTestFixture
import org.junit.Test

class DocumentSynchronizationTest : CodyIntegrationTestFixture() {

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

    val tempFile = myFixture.createFile("tempFile.java", beforeContent)
    val myUri = tempFile.url
    myFixture.configureByText("tempFile.java", beforeContent)

    insertBeforeText(beforeContent)

    val document = myFixture.editor.document
    // Write our initial content to the Editor
    WriteCommandAction.runWriteCommandAction(project) { document.setText(beforeContent) }

    // Perform our editing action.
    WriteCommandAction.runWriteCommandAction(project) {
      val offset = document.text.indexOf('@')
      document.replaceString(offset, offset + 1, "!")
    }

    // Ensure that the Editor's after-text matches expected
    assertEquals(expectedContent, document.text)

    CodyAgentService.withAgent(project) { agent ->
      agent.server.awaitPendingPromises() // Wait for Agent to complete its computations.

      val result =
          agent.server.testingRequestWorkspaceDocuments(GetDocumentsParams(uris = listOf(myUri)))

      result.thenAccept { response ->
        // There should be one document in the response.
        assertEquals(1, response.documents.size)
        // It should have our URI.
        val agentDocument = response.documents[0]
        assertEquals(myUri, agentDocument.uri)

        // It should have the same content as the Editor's after-text.
        assertEquals(expectedContent, agentDocument.content)
      }
    }
  }

  private fun insertBeforeText(content: String) {
    WriteCommandAction.runWriteCommandAction(project) {
      var text = content
      var caretOffset = -1
      var selectionStart = -1
      var selectionEnd = -1

      // Find and remove caret position marker "@"
      val caretIndex = text.indexOf("@")
      if (caretIndex != -1) {
        caretOffset = caretIndex
        text = text.removeRange(caretIndex, caretIndex + 1)
      }

      // Find and remove selection range marker "!"
      val selectionIndex = text.indexOf("!")
      if (caretIndex != -1 && selectionIndex != -1) {
        selectionStart = caretOffset
        selectionEnd = selectionIndex - 1 // Adjust for the removal of "!"
        text = text.removeRange(selectionIndex, selectionIndex + 1)
      }

      myFixture.editor.document.setText(text)

      // Set caret position if specified
      if (caretOffset != -1) {
        myFixture.editor.caretModel.moveToOffset(caretOffset)
      }

      // Set selection range if specified
      if (selectionStart != -1 && selectionEnd != -1) {
        myFixture.editor.selectionModel.setSelection(selectionStart, selectionEnd)
      }
    }
  }
}
