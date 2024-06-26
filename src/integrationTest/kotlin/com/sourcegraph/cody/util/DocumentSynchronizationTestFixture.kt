package com.sourcegraph.cody.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.GetDocumentsParams
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import java.util.concurrent.CompletableFuture

abstract class DocumentSynchronizationTestFixture : CodyIntegrationTestFixture() {

  protected fun runDocumentSynchronizationTest(
      beforeContent: String,
      expectedContent: String,
      writeAction: (Editor) -> Unit
  ) {
    val tempFile = myFixture.createFile("tempFile.java", beforeContent)
    configureFixtureWithFile(tempFile)
    setCaretAndSelection()

    val editor = myFixture.editor // Will not be set until we configure the fixture above.
    val document = editor.document

    WriteCommandAction.runWriteCommandAction(project) {
      // Execute the test-specific editing operation.
      writeAction(editor)
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

      val tempUri = ProtocolTextDocument.uriFor(tempFile)
      val result =
          agent.server.testingRequestWorkspaceDocuments(GetDocumentsParams(uris = listOf(tempUri)))

      result
          .thenAccept { response ->
            // There should be one document in the response.
            assertEquals(1, response.documents.size)
            // It should have our URI.
            val agentDocument = response.documents[0]
            assertEquals(tempUri, agentDocument.uri)
            // It should have the same content as the Editor's after-text.
            assertEquals(expectedContent, agentDocument.content)
            future.complete(null)
          }
          .exceptionally { ex ->
            future.completeExceptionally(ex)
            null
          }
    }
    // Block the test until Agent has responded.
    future.get()
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
