package com.sourcegraph.cody.agent.protocol

import com.intellij.codeInsight.codeVision.ui.popup.layouter.bottom
import com.intellij.codeInsight.codeVision.ui.popup.layouter.right
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Point
import java.nio.file.FileSystems
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class ProtocolTextDocument
private constructor(
    val uri: String,
    val content: String? = null,
    val selection: Range? = null,
    val visibleRange: Range? = null,
    val contentChanges: List<ProtocolTextDocumentContentChangeEvent>? = null,
    val testing: TestingParams? = null,
    vf: VirtualFile? = null
) {
  init {
    if (!ApplicationManager.getApplication().isDispatchThread) {
      throw IllegalStateException("ProtocolTextDocument must be be created on EDT")
    }
    vf?.let { uriToVSCodeUriMap[uriFor(it)] = uri }
  }

  companion object {
    private val uriToVSCodeUriMap = ConcurrentHashMap<String, String>()

    @RequiresEdt
    private fun getTestingParams(
        uri: String,
        content: String? = null,
        selection: Range? = null,
        selectedText: String? = null
    ): TestingParams? {
      if (!TestingParams.doIncludeTestingParam) {
        return null
      }
      return TestingParams(
          selectedText = selectedText,
          sourceOfTruthDocument =
              ProtocolTextDocument(
                  uri = uri,
                  content = content,
                  selection = selection,
              ))
    }

    @RequiresEdt
    private fun getSelection(editor: Editor): Range {
      val selectionModel = editor.selectionModel
      val beforeStartLines =
          editor.document.text.substring(0, selectionModel.selectionStart).lines()
      val beforeEndLines = editor.document.text.substring(0, selectionModel.selectionEnd).lines()
      return Range(
          Position(max(0, beforeStartLines.size - 1), beforeStartLines.last().length),
          Position(max(0, beforeEndLines.size - 1), beforeEndLines.last().length))
    }

    @RequiresEdt
    private fun getVisibleRange(editor: Editor): Range {
      val visibleArea = editor.scrollingModel.visibleArea

      val startOffset = editor.xyToLogicalPosition(visibleArea.location)
      val startOffsetLine = max(startOffset.line, 0)
      val startOffsetColumn = max(startOffset.column, 0)

      val endOffset = editor.xyToLogicalPosition(Point(visibleArea.right, visibleArea.bottom))
      val endOffsetLine = max(0, min(endOffset.line, editor.document.lineCount - 1))
      val endOffsetColumn = min(endOffset.column, editor.document.getLineEndOffset(endOffsetLine))

      return Range(
          Position(startOffsetLine, startOffsetColumn), Position(endOffsetLine, endOffsetColumn))
    }

    @JvmStatic
    @RequiresEdt
    fun fromEditorWithOffsetSelection(
        editor: Editor,
        newPosition: LogicalPosition
    ): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
      val position = newPosition.codyPosition()
      val uri = vsCodeUriFor(file)
      val selection = Range(position, position)
      return ProtocolTextDocument(
          uri = uri,
          selection = selection,
          testing = getTestingParams(uri, selection = selection, selectedText = ""))
    }

    @JvmStatic
    @RequiresEdt
    fun fromEditorWithRangeSelection(editor: Editor, event: SelectionEvent): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null

      val uri = uriFor(file)
      val selection =
          editor.document.codyRange(event.newRange.startOffset, event.newRange.endOffset)

      return ProtocolTextDocument(
          uri = uri,
          selection = selection,
          testing =
              getTestingParams(
                  uri = uri,
                  content = editor.document.text,
                  selection = selection,
                  selectedText = editor.selectionModel.selectedText))
    }

    private val isFullDocumentSyncEnabled =
        System.getProperty("cody-agent.fullDocumentSyncEnabled") == "true"

    @JvmStatic
    @RequiresEdt
    fun fromEditorForDocumentEvent(editor: Editor, event: DocumentEvent): ProtocolTextDocument? {

      val file = FileDocumentManager.getInstance().getFile(event.document) ?: return null
      val uri = uriFor(file)
      val selection = event.document.codyRange(editor.caretModel.offset, editor.caretModel.offset)

      val content =
          if (isFullDocumentSyncEnabled) {
            event.document.text
          } else {
            null
          }
      return ProtocolTextDocument(
          uri = uri,
          content = content,
          selection = selection,
          contentChanges =
              if (isFullDocumentSyncEnabled) {
                null
              } else {
                // IMPORTANT: note that we can't use `event.document` helpers to compute the end
                // position because `event.document.text` includes the latest change
                // (`event.newFragment`). Instead, we manually compute the end position based on
                // `event.oldFragment`.
                val start = event.document.codyPosition(event.offset)
                val endLine = start.line + event.oldFragment.count { it == '\n' }
                val endCharacter: Int =
                    if (endLine == start.line) {
                      start.character.toInt() + event.oldFragment.length
                    } else {
                      event.oldFragment.length - event.oldFragment.lastIndexOf('\n') - 1
                    }
                val end = Position(endLine.toInt(), endCharacter)
                listOf(
                    ProtocolTextDocumentContentChangeEvent(
                        Range(start, end), event.newFragment.toString()))
              },
          testing =
              getTestingParams(
                  uri, selection = selection, content = content ?: event.document.text))
    }

    @JvmStatic
    @RequiresEdt
    fun fromEditor(editor: Editor): ProtocolTextDocument? {
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
      return fromVirtualFile(editor, file)
    }

    @JvmStatic
    @RequiresEdt
    fun fromVirtualFile(
        editor: Editor,
        file: VirtualFile,
    ): ProtocolTextDocument {
      val content = FileDocumentManager.getInstance().getDocument(file)?.text
      val uri = vsCodeUriFor(file)
      val selection = getSelection(editor)
      return ProtocolTextDocument(
          uri = uri,
          content = content,
          selection = selection,
          visibleRange = getVisibleRange(editor),
          testing = getTestingParams(uri = uri, content = content, selection = selection))
    }

    @JvmStatic
    @RequiresEdt
    fun fromVirtualFile(
        originalUri: String,
        file: VirtualFile,
    ): ProtocolTextDocument {
      val content = FileDocumentManager.getInstance().getDocument(file)?.text
      val selection = null

      return ProtocolTextDocument(
          uri = originalUri,
          content = content,
          selection = selection,
          visibleRange = null,
          testing = getTestingParams(uri = originalUri, content = content, selection = selection),
          vf = file)
    }

    @JvmStatic
    @RequiresEdt
    fun vsCodeUriFor(file: VirtualFile): String {
      val uri = uriFor(file)
      return uriToVSCodeUriMap[uri] ?: uri
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

private fun Document.codyPosition(offset: Int): Position {
  val line = this.getLineNumber(offset)
  val lineStartOffset = this.getLineStartOffset(line)
  val character = offset - lineStartOffset
  return Position(line, character)
}

private fun Document.codyRange(startOffset: Int, endOffset: Int): Range {
  val startLine = this.getLineNumber(startOffset)
  val lineStartOffset1 = this.getLineStartOffset(startLine)
  val startCharacter = startOffset - lineStartOffset1

  val endLine = this.getLineNumber(endOffset)
  val lineStartOffset2 =
      if (startLine == endLine) {
        lineStartOffset1
      } else {
        this.getLineStartOffset(endLine)
      }
  val endCharacter = endOffset - lineStartOffset2

  return Range(Position(startLine, startCharacter), Position(endLine, endCharacter))
}

// Logical positions are 0-based (!), just like in VS Code.
private fun LogicalPosition.codyPosition(): Position {
  return Position(this.line, this.column)
}
