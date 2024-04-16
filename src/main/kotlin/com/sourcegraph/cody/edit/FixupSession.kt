package com.sourcegraph.cody.edit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.CommandExecuteParams
import com.sourcegraph.cody.agent.protocol.CodyTaskState
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.GetFoldingRangeParams
import com.sourcegraph.cody.agent.protocol.Position
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.agent.protocol.WorkspaceEditParams
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
    ApplicationManager.getApplication().invokeLater {
      Disposer.register(controller, this)
    }
  }

  fun commandCallbacks(): Map<String, () -> Unit> = lensActionCallbacks

  override fun dispose() {
    lensGroup?.dispose()
    lensGroup = null
    rangeMarkers.forEach { it.dispose() }
    rangeMarkers.clear()
    try {
      fixupService.removeSession(this)
    } catch (x: Exception) {
      logger.warn("Error while removing session", x)
    }
  }

  @RequiresEdt
  private fun triggerDocumentCodeAsync() {
    // This caret lookup requires us to be on the EDT.
    val caret = editor.caretModel.primaryCaret.offset
    CodyAgentService.withAgent(project) { agent ->
      workAroundUninitializedCodebase()
      // Force a round-trip to get folding ranges before showing lenses.
      ensureSelectionRange(agent, caret)
      showWorkingGroup()
      // All this because we can get the workspace/edit before the request returns!
      fixupService.addSession(this) // puts in Pending
      makeEditingRequest(agent)
          .handle { result, error ->
            if (error != null || result == null) {
              // TODO: Adapt logic from CodyCompletionsManager.handleError
              logger.warn("Error while generating doc string: $error")
              fixupService.removeSession(this)
            } else {
              taskId = result.id
              // Sometimes even though we get a folding range back, we don't get a selection range.
              if (result.selectionRange.start.isZero() && result.selectionRange.end.isZero()) {
                logger.warn("Empty selection range returned by Agent: $result")
              } else {
                selectionRange = result.selectionRange
              }
              fixupService.addSession(this)
            }
            null
          }
          .exceptionally { error: Throwable? ->
            if (!(error is CancellationException || error is CompletionException)) {
              logger.warn("Error while generating doc string: $error")
            }
            fixupService.removeSession(this)
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
      CodyAgentCodebase.getInstance(project).onFileOpened(project, file)
    } else {
      logger.warn("No virtual file associated with $document")
    }
  }

  private fun ensureSelectionRange(agent: CodyAgent, caret: Int) {
    val url = getDocumentUrl() ?: return
    val future = CompletableFuture<Unit>()
    agent.server.getFoldingRanges(GetFoldingRangeParams(uri = url)).handle { result, error ->
      if (result != null && error == null) {
        selectionRange = findRangeEnclosing(result.ranges, caret)
      }
      // Make sure we have SOME selection range near the caret.
      // Otherwise, we wind up with the lenses and insertion at top of file.
      if (selectionRange == null) {
        logger.warn("Unable to find enclosing folding range at $caret in $url")
        selectionRange =
            Range(Position.fromOffset(document, caret), Position.fromOffset(document, caret))
      }
      future.complete(null)
      publishProgressOnEdt(CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES)
    }
    future.get() // Block until we have a selection range.
  }

  private fun getDocumentUrl(): String? {
    val virtualFile = FileDocumentManager.getInstance().getFile(document)
    if (virtualFile == null) {
      logger.warn("No URI for document: $document")
      return null
    }
    return virtualFile.url
  }

  private fun findRangeEnclosing(ranges: List<Range>, offset: Int): Range? {
    return ranges.firstOrNull { range ->
      range.start.toOffset(document) <= offset && range.end.toOffset(document) >= offset
    }
  }

  fun update(task: EditTask) {
    logger.warn("Task updated: $task")
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
    if (!ApplicationManager.getApplication().isDispatchThread) { // integration test
      future.get()
    }
  }

  @RequiresBackgroundThread
  private fun showWorkingGroup() {
    showLensGroup(LensGroupFactory(this).createTaskWorkingGroup())
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
  }

  @RequiresBackgroundThread
  private fun showAcceptGroup() {
    showLensGroup(LensGroupFactory(this).createAcceptGroup())
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ACCEPT_GROUP)
  }

  fun finish() {
    try {
      controller.removeSession(this)
      rangeMarkers.forEach { it.dispose() }
      rangeMarkers.clear()
    } catch (x: Exception) {
      logger.debug("Session cleanup error", x)
    }
    Disposer.dispose(this)
  }

  /** Subclass sends a fixup command to the agent, and returns the initial task. */
  abstract fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask>

  fun accept() {
    CodyAgentService.withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(COMMAND_ACCEPT, listOf(taskId!!)))
    }
    finish()
  }

  fun cancel() {
    CodyAgentService.withAgent(project) { agent ->
      agent.server.commandExecute(CommandExecuteParams(COMMAND_CANCEL, listOf(taskId!!)))
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
      agent.server.commandExecute(CommandExecuteParams(COMMAND_UNDO, listOf(taskId!!)))
    }
    undoEdits()
    publishProgress(CodyInlineEditActionNotifier.TOPIC_PERFORM_UNDO)
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
        publishProgress(CodyInlineEditActionNotifier.TOPIC_WORKSPACE_EDIT)
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

  private fun publishProgress(topic: Topic<CodyInlineEditActionNotifier>) {
    ApplicationManager.getApplication().executeOnPooledThread {
      project.messageBus
          .syncPublisher(topic)
          .afterAction(CodyInlineEditActionNotifier.Context(session = this))
    }
  }

  private fun publishProgressOnEdt(topic: Topic<CodyInlineEditActionNotifier>) {
    ApplicationManager.getApplication().invokeLater {
      project.messageBus
          .syncPublisher(topic)
          .afterAction(CodyInlineEditActionNotifier.Context(session = this))
    }
  }

  companion object {
    // Lens actions the user can take; we notify the Agent when they are taken.
    const val COMMAND_ACCEPT = "cody.fixup.codelens.accept"
    const val COMMAND_CANCEL = "cody.fixup.codelens.cancel"
    const val COMMAND_RETRY = "cody.fixup.codelens.retry"
    const val COMMAND_DIFF = "cody.fixup.codelens.diff"
    const val COMMAND_UNDO = "cody.fixup.codelens.undo"

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
