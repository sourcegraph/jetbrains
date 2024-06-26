package com.sourcegraph.cody.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.GetDocumentsParams
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import java.util.concurrent.CompletableFuture

/**
 * Lets you specify before/after tests that modify the document and check the Agent's copy. You can
 * specify the starting caret with "^" and optionally the selection with "@".
 */
abstract class DocumentSynchronizationTestFixture : CodyIntegrationTestFixture() {

  protected fun runDocumentSynchronizationTest(
      beforeSpec: String,
      expectedSpec: String,
      writeAction: (Editor) -> Unit
  ) {
    val expectedContent = expectedSpec.trimIndent().removePrefix("\n")
    var content = beforeSpec.trimIndent().removePrefix("\n")

    var caretOffset = -1
    var selectionStart = -1
    var selectionEnd = -1

    val caretIndex = content.indexOf("^")
    if (caretIndex != -1) {
      caretOffset = caretIndex
      content = content.removeRange(caretIndex, caretIndex + 1)
    }

    val selectionIndex = content.indexOf("@")
    if (caretIndex != -1 && selectionIndex != -1) {
      selectionStart = caretOffset
      selectionEnd = selectionIndex - 1 // Adjust for the removal of "@"
      content = content.removeRange(selectionIndex, selectionIndex + 1)
    }

    val tempFile = myFixture.createFile("tempFile.java", content)
    configureFixtureWithFile(tempFile)
    setCaretAndSelection(caretOffset, selectionStart, selectionEnd)

    WriteCommandAction.runWriteCommandAction(project) {
      // Execute the test-specific editing operation.
      writeAction(myFixture.editor)
    }

    // Make sure our own copy of the document was edited properly.
    assertEquals(expectedContent, myFixture.editor.document.text)

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
