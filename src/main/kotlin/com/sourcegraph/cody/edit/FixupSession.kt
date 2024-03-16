package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.*
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensWidgetGroup
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

/**
 * Common functionality for commands that let the agent edit the code inline, such as adding a doc
 * string, or fixing up a region according to user instructions.
 */
abstract class FixupSession(val controller: FixupService, val editor: Editor) : Disposable {
  private val logger = Logger.getInstance(FixupSession::class.java)

  // This is passed back by the Agent when we initiate the editing task.
  var taskId: String? = null

  var performedEdits = false
    private set

  private var lensGroup: LensWidgetGroup? = null

  private var selectionRange: Range? = null

  private val lensActionCallbacks =
      mapOf(
          COMMAND_ACCEPT to { accept() },
          COMMAND_CANCEL to { cancel() },
          COMMAND_RETRY to { retry() },
          COMMAND_DIFF to { diff() },
          COMMAND_UNDO to { undo() },
      )

  init {
    triggerDocumentCodeAsync()
  }

  fun commandCallbacks(): Map<String, () -> Unit> = lensActionCallbacks

  @RequiresEdt
  private fun triggerDocumentCodeAsync() {
      // This caret lookup requires us to be on the EDT.
    val caret = editor.caretModel.primaryCaret.offset

    FixupService.backgroundThread {
      val project = editor.project!!
      CodyAgentService.withAgent(project) { agent ->
        workAroundUninitializedCodebase(editor)
        ensureSelectionRange(agent, editor, caret)
        showWorkingGroup()
        // All this because we can get the workspace/edit before the request returns!
        FixupService.getInstance(project).addSession(this) // puts in Pending
        makeEditingRequest(agent)
            .handle { result, error ->
              if (error != null || result == null) {
                // TODO: Adapt logic from CodyCompletionsManager.handleError
                logger.warn("Error while generating doc string: $error")
                FixupService.getInstance(project).removeSession(this)
              } else {
                taskId = result.id
                selectionRange = result.selectionRange
                FixupService.getInstance(project).addSession(this)
              }
              null
            }
            .exceptionally { error: Throwable? ->
              if (!(error is CancellationException || error is CompletionException)) {
                logger.warn("Error while generating doc string: $error")
              }
              FixupService.getInstance(project).removeSession(this)
              null
            }
            .completeOnTimeout(null, 3, TimeUnit.SECONDS)
      }
    }
  }

  // We're consistently triggering the 'retrieved codebase context before initialization' error
  // in ContextProvider.ts. It's a different initialization path from completions & chat.
  // Calling onFileOpened forces the right initialization path.
  private fun workAroundUninitializedCodebase(editor: Editor) {
    val file = FileDocumentManager.getInstance().getFile(editor.document)!!
    val project = editor.project!!
    CodyAgentCodebase.getInstance(project).onFileOpened(project, file)
  }

  private fun ensureSelectionRange(agent: CodyAgent, editor: Editor, caret: Int) {
    val url = getDocumentUrl(editor)
    if (url != null) {
      agent.server.getFoldingRanges(GetFoldingRangeParams(uri = url)).handle { result, error ->
        if (result != null && error == null) {
          selectionRange = findRangeEnclosing(result.ranges, caret)
        }
        // Make sure we have SOME selection range near the caret.
        // Otherwise, we wind up with the lenses and insertion at top of file.
        if (selectionRange == null) {
          logger.warn("Unable to find enclosing folding range at $caret in $url")
          selectionRange =
              Range(
                  Position.fromOffset(editor.document, caret),
                  Position.fromOffset(editor.document, caret))
        }
      }
    }
  }

  private fun getDocumentUrl(editor: Editor): String? {
    val document = editor.document
    val virtualFile = FileDocumentManager.getInstance().getFile(document)
    if (virtualFile == null) {
      logger.warn("No URI for document: $document")
      return null
    }
    return virtualFile.url
  }

  private fun findRangeEnclosing(ranges: List<Range>, offset: Int): Range? {
    return ranges.firstOrNull { range ->
      range.start.toOffset(editor.document) <= offset &&
          range.end.toOffset(editor.document) >= offset
    }
  }

  fun update(task: EditTask) {
    logger.warn("Task updated: $task")
    when (task.state) {
      CodyTaskState.Idle -> {}
      CodyTaskState.Working,
      CodyTaskState.Inserting,
      CodyTaskState.Applying,
      CodyTaskState.Formatting -> {}
      // Tasks remain in this state until explicit accept/undo/cancel.
      CodyTaskState.Applied -> showAcceptGroup()
      // Then they transition to finished.
      CodyTaskState.Finished -> {}
      CodyTaskState.Error -> {}
      CodyTaskState.Pending -> {}
    }
  }

  /** Notification that the Agent has deleted the task. Clean up if we haven't yet. */
  fun taskDeleted() {
    finish()
  }

  private fun showLensGroup(group: LensWidgetGroup) {
    lensGroup?.let { if (!it.isDisposed.get()) Disposer.dispose(it) }
    lensGroup = group
    var range = selectionRange
    if (range == null) {
        // Be defensive, as the protocol has been fragile with respect to selection ranges.
      logger.warn("No selection range for session: $this")
      // Last-ditch effort to show it somewhere other than top of file.
      val position = Position(editor.caretModel.currentCaret.logicalPosition.line, 0)
      range = Range(start = position, end = position)
    } else {
      // The actual insertion point is on the line above.
      val position = Position(range.start.line - 1, 0)
      range = Range(start = position, end = position)
    }
    group.show(range)
  }

  private fun showWorkingGroup() {
    showLensGroup(LensGroupFactory(this).createTaskWorkingGroup())
  }

  private fun showAcceptGroup() {
    showLensGroup(LensGroupFactory(this).createAcceptGroup())
  }

  fun finish() {
    controller.removeSession(this)
    Disposer.dispose(this)
  }

  /** Subclass sends a fixup command to the agent, and returns the initial task. */
  abstract fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask>

  abstract fun accept()

  abstract fun retry()

  abstract fun cancel()

  abstract fun diff()

  abstract fun undo()

  fun performInlineEdits(edits: List<TextEdit>) {
    // TODO: This is an artifact of the update to concurrent editing tasks.
    // We do need to mute any LensGroup listeners, but this is an ugly way to do it.
    // We may need a Document-level list of listeners to mute.
    lensGroup?.withListenersMuted {
      if (!controller.isEligibleForInlineEdit(editor)) {
        return@withListenersMuted logger.warn("Inline edit not eligible")
      }
      WriteCommandAction.runWriteCommandAction(editor.project ?: return@withListenersMuted) {
        val doc: Document = editor.document
        val project = editor.project ?: return@runWriteCommandAction
        // TODO: For all 3 of these, we should use a marked range to track it over edits.
        for (edit in edits) {
          // TODO: handle options if present (currently just undo bounds)
          when (edit.type) {
            "replace" -> performReplace(doc, edit)
            "insert" -> performInsert(doc, edit)
            "delete" -> performDelete(doc, edit)
            else -> logger.warn("Unknown edit type: ${edit.type}")
          }
          // TODO: Group all the edits into a single UndoableAction.
          UndoManager.getInstance(project)
              .undoableActionPerformed(FixupUndoableAction.from(editor, edit))
        }
      }
    }
  }

  private fun performReplace(doc: Document, edit: TextEdit) {
    val (start, end) = edit.range?.toOffsets(doc) ?: return
    val text = edit.value
    if (text == null) {
      logger.warn("Invalid edit operation params: $edit")
      return
    }
    performedEdits = true
    doc.replaceString(start, end, text)
  }

  protected open fun performInsert(doc: Document, edit: TextEdit) {
    val start = edit.position?.toOffset(doc) ?: edit.range?.start?.toOffset(doc)
    val text = edit.value
    if (start == null || text == null) {
      logger.warn("Invalid edit operation params: $edit")
      return
    }
    // Set this flag before we make the edit, since callbacks are called synchronously.
    performedEdits = true
    doc.insertString(start, text)
  }

  private fun performDelete(doc: Document, edit: TextEdit) {
    val (start, end) = edit.range?.toOffsets(doc) ?: return
    performedEdits = true
    doc.deleteString(start, end)
  }

  protected fun undoEdits() {
    val project = editor.project ?: return
    if (project.isDisposed) return
    val fileEditor = getEditorForDocument(editor.document, project)
    val undoManager = UndoManager.getInstance(project)
    if (undoManager.isUndoAvailable(fileEditor)) {
      undoManager.undo(fileEditor)
    }
  }

  private fun getEditorForDocument(document: Document, project: Project): FileEditor? {
    val file = FileDocumentManager.getInstance().getFile(document)
    return file?.let { getCurrentFileEditor(project, it) }
  }

  private fun getCurrentFileEditor(project: Project, file: VirtualFile): FileEditor? {
    return FileEditorManager.getInstance(project).getEditors(file).firstOrNull()
  }

  companion object {
    const val COMMAND_ACCEPT = "cody.fixup.codelens.accept"
    const val COMMAND_CANCEL = "cody.fixup.codelens.cancel"
    const val COMMAND_RETRY = "cody.fixup.codelens.retry"
    const val COMMAND_DIFF = "cody.fixup.codelens.diff"
    const val COMMAND_UNDO = "cody.fixup.codelens.undo"

    // TODO: Register the hotkeys now that we are displaying them.
    fun getHotKey(command: String): String {
      // Claude picked these key bindings for me.
      val mac = SystemInfoRt.isMac
      return when (command) {
        COMMAND_ACCEPT -> if (mac) "⌥⌘A" else "Ctrl+Alt+A"
        COMMAND_CANCEL -> if (mac) "⌥⌘R" else "Ctrl+Alt+R"
        COMMAND_DIFF -> if (mac) "⌘D" else "Ctrl+D" // JB default
        COMMAND_RETRY -> if (mac) "⌘Z" else "Ctrl+Z" // JB default
        COMMAND_UNDO -> if (mac) "⌥⌘C" else "Alt+Ctrl+C"
        else -> ""
      }
    }
  }
}
