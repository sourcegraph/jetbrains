package com.sourcegraph.cody

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.GetDocumentsParams
import com.sourcegraph.cody.util.CodyIntegrationTestFixture
import org.junit.Test
import java.util.concurrent.CompletableFuture

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
    configureFixtureWithFile(tempFile)
    setCaretAndSelection()

    val editor = myFixture.editor // Will not be set until we configure the fixture above.
    val document = editor.document

    WriteCommandAction.runWriteCommandAction(project) {
      // This is the test-specific editing operation to test.
      // TODO: Move everything else here except before/after text, into the test fixture.
      document.insertString(editor.caretModel.offset, "!")
    }

    // Make sure our own copy of the document was edited properly.
    assertEquals(expectedContent, document.text)

    checkAgentResults(tempFile, expectedContent)
  }

  private fun checkAgentResults(tempFile: VirtualFile, expectedContent: String) {
    // Verify that Agent has the correct content, caret, and optionally, selection.
    val future = CompletableFuture<Void>()
    CodyAgentService.withAgent(project) { agent ->
      agent.server.awaitPendingPromises()

      val result =
          agent.server.testingRequestWorkspaceDocuments(
              GetDocumentsParams(uris = listOf(tempFile.url)))

      result.thenAccept { response ->
        // There should be one document in the response.
        assertEquals(1, response.documents.size)
        // It should have our URI.
        val agentDocument = response.documents[0]
        assertEquals(tempFile.url, agentDocument.uri)

        // It should have the same content as the Editor's after-text.
        assertEquals(expectedContent, agentDocument.content)
        future.complete(null)
      }.exceptionally { ex ->
        future.completeExceptionally(ex)
        null
      }
    }
    future.get() // Wait for the CompletableFuture to complete
  }

  private fun setCaretAndSelection() {
    WriteCommandAction.runWriteCommandAction(project) {
      var text = myFixture.editor.document.text
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
