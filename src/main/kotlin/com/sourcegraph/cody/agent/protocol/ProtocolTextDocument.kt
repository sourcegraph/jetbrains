package com.sourcegraph.cody.agent.protocol

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.agent.protocol.util.Rfc3986UriEncoder

class ProtocolTextDocument
private constructor(
    var uri: String,
    var content: String?,
    var textDocumentContentChangeEvents: List<TextDocumentContentChangeEvent>?,
    var selection: Range?,
) {

  companion object {

    private fun getSelection(editor: Editor): Range {
      val selectionModel = editor.selectionModel
      val selectionStartPosition =
          selectionModel.selectionStartPosition?.let { editor.visualToLogicalPosition(it) }
      val selectionEndPosition =
          selectionModel.selectionEndPosition?.let { editor.visualToLogicalPosition(it) }
      if (selectionStartPosition != null && selectionEndPosition != null) {
        return Range(
            Position(selectionStartPosition.line, selectionStartPosition.column),
            Position(selectionEndPosition.line, selectionEndPosition.column))
      }
      val caret = editor.caretModel.primaryCaret
      val position = Position(caret.logicalPosition.line, caret.logicalPosition.column)
      // A single-offset caret is a selection where end == start.
      return Range(position, position)
    }

    @JvmStatic
    fun fromEditor(editor: Editor, event: DocumentEvent? = null): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document)
      return if (file != null) fromVirtualFile(editor, file, event) else null
    }

    @JvmStatic
    fun fromVirtualFile(
        editor: Editor,
        file: VirtualFile,
        event: DocumentEvent? = null,
    ): ProtocolTextDocument {
      val text = FileDocumentManager.getInstance().getDocument(file)?.text
      val selection = getSelection(editor)

      val textDocumentContentChangeEvent =
          if (event != null) {
            TextDocumentContentChangeEvent(
                text = event.newFragment.toString(),
                rangeLength = event.oldLength,
                rangeOffset = event.offset)
          } else null

      return ProtocolTextDocument(
          uriFor(file), text, listOfNotNull(textDocumentContentChangeEvent), selection)
    }

    fun uriFor(file: VirtualFile): String {
      return Rfc3986UriEncoder.encode(file.url)
    }
  }
}
