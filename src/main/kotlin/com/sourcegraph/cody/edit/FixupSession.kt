package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
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
import com.sourcegraph.cody.agent.protocol.CodyTaskState
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.GetFoldingRangeParams
import com.sourcegraph.cody.agent.protocol.Position
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.agent.protocol.TaskIdParam
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.agent.protocol.WorkspaceEditParams
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensWidgetGroup
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Common functionality for commands that let the agent edit the code inline, such as adding a doc
 * string, or fixing up a region according to user instructions.
 */
abstract class FixupSession(
    val controller: FixupService,
    val editor: Editor,
    val project: Project,
    val document: Document
) : Disposable {
  private val logger = Logger.getInstance(FixupSession::class.java)
  private val fixupService = FixupService.getInstance(project)

  // This is passed back by the Agent when we initiate the editing task.
  var taskId: String? = null

  private var performedEdits = false

  var lensGroup: LensWidgetGroup? = null
    private set

  var selectionRange: Range? = null

  private var rangeMarkers: MutableSet<RangeMarker> = mutableSetOf()

  private val showedAcceptLens = AtomicBoolean(false)

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
    // Kotlin doesn't like leaking 'this' before the constructors are finished.
    ApplicationManager.getApplication().invokeLater { Disposer.register(controller, this) }
  }

  fun commandCallbacks(): Map<String, () -> Unit> = lensActionCallbacks

  override fun dispose() {
    try {
      lensGroup?.dispose()
      lensGroup = null
    } catch (x: Exception) {
      logger.warn("Error while disposing lenses", x)
    }
    rangeMarkers.forEach { it.dispose() }
    rangeMarkers.clear()
    try {
      fixupService.clearActiveSession()
    } catch (x: Exception) {
      logger.warn("Error while removing session", x)
    }
  }

  @RequiresEdt
  private fun triggerDocumentCodeAsync() {
    // Those lookups require us to be on the EDT.
    val file = FileDocumentManager.getInstance().getFile(document)
    val textFile = file?.let { ProtocolTextDocument.fromVirtualFile(editor, it) } ?: return

    CodyAgentService.withAgent(project) { agent ->
      workAroundUninitializedCodebase()
      // Force a round-trip to get folding ranges before showing lenses.
      ensureSelectionRange(agent, textFile)
      showWorkingGroup()
      // All this because we can get the workspace/edit before the request returns!
      fixupService.setActiveSession(this)
      makeEditingRequest(agent)
          .handle { result, error ->
            if (error != null || result == null) {
              // TODO: Adapt logic from CodyCompletionsManager.handleError
              logger.warn("Error while generating doc string: $error")
              fixupService.clearActiveSession()
            } else {
              taskId = result.id
              selectionRange = result.selectionRange
              fixupService.setActiveSession(this)
            }
            null
          }
          .exceptionally { error: Throwable? ->
            if (!(error is CancellationException || error is CompletionException)) {
              logger.warn("Error while generating doc string: $error")
            }
            finish()
            null
          }
          .completeOnTimeout(null, 3, TimeUnit.SECONDS)
    }
  }

  // We're consistently triggering the 'retrieved codebase context before initialization' error
  // in ContextProvider.ts. It's a different initialization path from completions & chat.
  // Calling onFileOpened forces the right initialization path.
  private fun workAroundUninitializedCodebase() {
    val file = FileDocumentManager.getInstance().getFile(document)
    if (file != null) {
      CodyAgentCodebase.getInstance(project).onFileOpened(file)
    } else {
      logger.warn("No virtual file associated with $document")
    }
  }

  private fun ensureSelectionRange(agent: CodyAgent, textFile: ProtocolTextDocument) {
    val selection = textFile.selection ?: return
    selectionRange = selection
    agent.server
        .getFoldingRanges(GetFoldingRangeParams(uri = textFile.uri, range = selection))
        .thenApply { result -> selectionRange = result.range }
        .get()
  }

  fun update(task: EditTask) {
    when (task.state) {
      // This is an internal state (parked/ready tasks) and we should never see it.
      CodyTaskState.Idle -> {}
      // These four may or may not all arrive, depending on the operation, testing, etc.
      // They are all sent in quick succession and any one can substitute for another.
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

  // N.B. Blocks calling thread until the lens group is shown,
  // which may require switching to the EDT. This is primarily to help smooth
  // integration testing, but also because there's no real harm blocking pool threads.
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
      val position = Position(range.start.line, 0)
      range = Range(start = position, end = position)
    }
    val future = group.show(range)
    // Make sure the lens is visible.
    ApplicationManager.getApplication().invokeLater {
      val logicalPosition = LogicalPosition(range.start.line, range.start.character)
      editor.scrollingModel.scrollTo(logicalPosition, ScrollType.CENTER)
    }
    if (!ApplicationManager.getApplication().isDispatchThread) { // integration test
      future.get()
    }
  }

  private fun showWorkingGroup() {
    showLensGroup(LensGroupFactory(this).createTaskWorkingGroup())
  }

  private fun showAcceptGroup() {
    showLensGroup(LensGroupFactory(this).createAcceptGroup())
  }

  fun finish() {
    try {
      controller.clearActiveSession()
      rangeMarkers.forEach { it.dispose() }
      rangeMarkers.clear()
    } catch (x: Exception) {
      logger.debug("Session cleanup error", x)
    }
    try {
      Disposer.dispose(this)
    } catch (x: Exception) {
      logger.warn("Error disposing fixup session $this", x)
    }
  }

  /** Subclass sends a fixup command to the agent, and returns the initial task. */
  abstract fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask>

  fun accept() {
    CodyAgentService.withAgent(project) { agent ->
      agent.server.acceptEditTask(TaskIdParam(taskId!!))
    }
    finish()
  }

  fun cancel() {
    CodyAgentService.withAgent(project) { agent ->
      agent.server.cancelEditTask(TaskIdParam(taskId!!))
    }
    if (performedEdits) {
      undo()
    } else {
      finish()
    }
  }

  abstract fun retry()

  abstract fun diff()

  fun undo() {
    CodyAgentService.withAgent(project) { agent ->
      agent.server.undoEditTask(TaskIdParam(taskId!!))
    }
    undoEdits()
    finish()
  }

  fun performWorkspaceEdit(workspaceEditParams: WorkspaceEditParams) {
    for (op in workspaceEditParams.operations) {
      // TODO: We need to support the file-level operations.
      when (op.type) {
        "create-file" -> {
          logger.warn("Workspace edit operation created a file: ${op.uri}")
        }
        "rename-file" -> {
          logger.warn("Workspace edit operation renamed a file: ${op.oldUri} -> ${op.newUri}")
        }
        "delete-file" -> {
          logger.warn("Workspace edit operation deleted a file: ${op.uri}")
        }
        "edit-file" -> {
          if (op.edits == null) {
            logger.warn("Workspace edit operation has no edits")
          } else {
            performInlineEdits(op.edits)
          }
        }
        else ->
            logger.warn(
                "DocumentCommand session received unknown workspace edit operation: ${op.type}")
      }
    }
  }

  fun performInlineEdits(edits: List<TextEdit>) {
    // TODO: This is an artifact of the update to concurrent editing tasks.
    // We do need to mute any LensGroup listeners, but this is an ugly way to do it.
    // There are multiple Lens groups; we need a Document-level listener list.
    lensGroup?.withListenersMuted {
      if (!controller.isEligibleForInlineEdit(editor)) {
        return@withListenersMuted logger.warn("Inline edit not eligible")
      }
      // Mark all the edit locations so the markers will move as we edit the document,
      // preserving the original positions of the edits.
      val markers = edits.mapNotNull { createMarkerForEdit(it) }
      val sortedEdits = edits.zip(markers).sortedByDescending { it.second.startOffset }
      // Apply the edits in a write action.
      WriteCommandAction.runWriteCommandAction(project) {
        for ((edit, marker) in sortedEdits) {
          when (edit.type) {
            "replace",
            "delete" -> ReplaceUndoableAction(this, edit, marker)
            "insert" -> InsertUndoableAction(this, edit, marker)
            else -> logger.warn("Unknown edit type: ${edit.type}")
          }
        }
      }
    }
  }

  private fun createMarkerForEdit(edit: TextEdit): RangeMarker? {
    val startOffset: Int
    val endOffset: Int
    when (edit.type) {
      "replace",
      "delete" -> {
        val range = edit.range ?: return null
        startOffset = document.getLineStartOffset(range.start.line) + range.start.character
        endOffset = document.getLineStartOffset(range.end.line) + range.end.character
      }
      "insert" -> {
        val position = edit.position ?: return null
        startOffset = document.getLineStartOffset(position.line) + position.character
        endOffset = startOffset
      }
      else -> return null
    }
    return createMarker(startOffset, endOffset)
  }

  fun createMarker(startOffset: Int, endOffset: Int): RangeMarker {
    return document.createRangeMarker(startOffset, endOffset).apply { rangeMarkers.add(this) }
  }

  fun removeMarker(marker: RangeMarker) {
    try {
      marker.dispose()
      rangeMarkers.remove(marker)
    } catch (x: Exception) {
      logger.debug("Error disposing marker $marker", x)
    }
  }

  private fun undoEdits() {
    if (project.isDisposed) return
    val fileEditor = getEditorForDocument()
    val undoManager = UndoManager.getInstance(project)
    if (undoManager.isUndoAvailable(fileEditor)) {
      undoManager.undo(fileEditor)
    }
  }

  private fun getEditorForDocument(): FileEditor? {
    val file = FileDocumentManager.getInstance().getFile(document)
    return file?.let { getCurrentFileEditor(it) }
  }

  private fun getCurrentFileEditor(file: VirtualFile): FileEditor? {
    return FileEditorManager.getInstance(project).getEditors(file).firstOrNull()
  }

  companion object {
    // Lens actions the user can take; we notify the Agent when they are taken.
    const val COMMAND_ACCEPT = "cody.fixup.codelens.accept"
    const val COMMAND_CANCEL = "cody.fixup.codelens.cancel"
    const val COMMAND_RETRY = "cody.fixup.codelens.retry"
    const val COMMAND_DIFF = "cody.fixup.codelens.diff"
    const val COMMAND_UNDO = "cody.fixup.codelens.undo"

    // JetBrains Actions that we fire when the lenses are clicked.
    const val ACTION_ACCEPT = "cody.inlineEditAcceptAction"
    const val ACTION_CANCEL = "cody.inlineEditCancelAction"
    const val ACTION_RETRY = "cody.inlineEditRetryAction"
    const val ACTION_DIFF = "cody.inlineEditDiffAction"
    const val ACTION_UNDO = "cody.inlineEditUndoAction"

    // TODO: Register the hotkeys now that we are displaying them.
    fun getHotKey(command: String): String {
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
