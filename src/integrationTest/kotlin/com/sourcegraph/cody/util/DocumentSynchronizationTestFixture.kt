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
    // Extract caret and selection markers
    var content = beforeContent
    var caretOffset = -1
    var selectionStart = -1
    var selectionEnd = -1

    // Find and remove caret position marker "^"
    val caretIndex = content.indexOf("^")
    if (caretIndex != -1) {
      caretOffset = caretIndex
      content = content.removeRange(caretIndex, caretIndex + 1)
    }

    // Find and remove selection range marker "@"
    val selectionIndex = content.indexOf("@")
    if (caretIndex != -1 && selectionIndex != -1) {
      selectionStart = caretOffset
      selectionEnd = selectionIndex - 1 // Adjust for the removal of "@"
      content = content.removeRange(selectionIndex, selectionIndex + 1)
    }

    val tempFile = myFixture.createFile("tempFile.java", content)
    configureFixtureWithFile(tempFile)
    setCaretAndSelection(caretOffset, selectionStart, selectionEnd)

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

  private fun setCaretAndSelection(caretOffset: Int, selectionStart: Int, selectionEnd: Int) {
    WriteCommandAction.runWriteCommandAction(project) {
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
