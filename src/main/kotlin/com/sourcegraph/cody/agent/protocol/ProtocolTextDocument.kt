package com.sourcegraph.cody.agent.protocol

import com.intellij.codeInsight.codeVision.ui.popup.layouter.bottom
import com.intellij.codeInsight.codeVision.ui.popup.layouter.right
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Point
import java.nio.file.FileSystems
import java.util.*
import kotlin.math.max
import kotlin.math.min

class ProtocolTextDocument
private constructor(
    val uri: String,
    val content: String? = null,
    val selection: Range? = null,
    val visibleRange: Range? = null,
    val contentChanges: List<ProtocolTextDocumentContentChangeEvent>? = null,
) {

  companion object {

    private fun getSelection(editor: Editor, isZeroBased: Boolean = false): Range {
      // For an empty document, return a zero-width selection at the start of the document.
      if (editor.document.lineCount == 0) return Range(Position(0, 0), Position(0, 0))
      // Subtract 1 to convert 1-based line numbers to 0-based line numbers.
      val baseline = if (isZeroBased) 1 else 0
      val selectionModel = editor.selectionModel
      val selectionStartPosition =
          selectionModel.selectionStartPosition?.let { editor.visualToLogicalPosition(it) }
      val selectionEndPosition =
          selectionModel.selectionEndPosition?.let { editor.visualToLogicalPosition(it) }
      if (selectionStartPosition != null && selectionEndPosition != null) {
        return Range(
            Position(selectionStartPosition.line - baseline, selectionStartPosition.column),
            Position(selectionEndPosition.line - baseline, selectionEndPosition.column)
        )
      }
      val caret = editor.caretModel.primaryCaret
      val position = Position(caret.logicalPosition.line - baseline, caret.logicalPosition.column)
      // A single-offset caret is a selection where end == start.
      return Range(position, position)
    }

    private fun getVisibleRange(editor: Editor): Range {
      val visibleArea = editor.scrollingModel.visibleArea

      val startOffset = editor.xyToLogicalPosition(visibleArea.location)
      // Minus 1 to convert 1-based line numbers to 0-based line numbers before sending to Agent.
      val startOffsetLine = max(startOffset.line - 1, 0)
      val startOffsetColumn = max(startOffset.column, 0)

      val endOffset = editor.xyToLogicalPosition(Point(visibleArea.right, visibleArea.bottom))
      val endOffsetLine = max(0, min(endOffset.line - 1, editor.document.lineCount - 1))
      val endOffsetColumn = min(endOffset.column, editor.document.getLineEndOffset(endOffsetLine))

      return Range(
          Position(startOffsetLine, startOffsetColumn),
          Position(endOffsetLine, endOffsetColumn)
      )
    }

    @JvmStatic
    fun fromEditorWithOffsetSelection(
        editor: Editor,
        newPosition: LogicalPosition
    ): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
      val position = Position(newPosition.line, newPosition.column)
      return ProtocolTextDocument(uri = uriFor(file), selection = Range(position, position))
    }

    @JvmStatic
    fun fromEditorWithRangeSelection(editor: Editor): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
      // Keep current selection as is when sending the document to the agent.
      return ProtocolTextDocument(uri = uriFor(file), selection = getSelection(editor, false))
    }

    @JvmStatic
    fun fromEditorForDocumentEvent(editor: Editor, event: DocumentEvent): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
      return ProtocolTextDocument(
          uri = uriFor(file),
          content = editor.document.text,
          selection = getSelection(editor, true),
          // TODO(olafurpg): let's implement incremental document synchronization later.
          //                 Incremental document synchronization is very difficult to get
          //                 100% right so we need to be careful.
          //          contentChanges =
          //              listOf(
          //                  ProtocolTextDocumentContentChangeEvent(
          //                      Range(
          //                          position(editor.document, event.offset),
          //                          position(editor.document, event.offset + event.oldLength)),
          //                      event.newFragment.toString()))
          )
    }

    @JvmStatic
    fun fromEditor(editor: Editor): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
      return fromVirtualFile(editor, file)
    }

    @JvmStatic
    fun fromVirtualFile(
        editor: Editor,
        file: VirtualFile,
    ): ProtocolTextDocument {
      val text = FileDocumentManager.getInstance().getDocument(file)?.text
      return ProtocolTextDocument(
          uri = uriFor(file),
          content = text,
          selection = getSelection(editor, true),
          visibleRange = getVisibleRange(editor)
      )
    }

    @JvmStatic
    private fun uriFor(file: VirtualFile): String {
      val uri = FileSystems.getDefault().getPath(file.path).toUri().toString()
      return uri.replace(Regex("file:///(\\w):/")) {
        val driveLetter =
            it.groups[1]?.value?.lowercase(Locale.getDefault()) ?: return@replace it.value
        "file:///$driveLetter%3A/"
      }
    }
  }
}
