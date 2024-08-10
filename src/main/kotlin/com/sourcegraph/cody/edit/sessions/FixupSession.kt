package com.sourcegraph.cody.edit.sessions

import FixupSessionDocumentListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
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
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier
import com.sourcegraph.cody.edit.EditCommandPrompt
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.edit.exception.EditCreationException
import com.sourcegraph.cody.edit.exception.EditExecutionException
import com.sourcegraph.cody.edit.fixupActions.FixupUndoableAction
import com.sourcegraph.cody.edit.fixupActions.InsertUndoableAction
import com.sourcegraph.cody.edit.fixupActions.ReplaceUndoableAction
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensWidgetGroup
import com.sourcegraph.cody.vscode.CancellationToken
import com.sourcegraph.utils.CodyEditorUtil
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import org.jetbrains.annotations.VisibleForTesting

/**
 * Common functionality for commands that let the agent edit the code inline, such as adding a doc
 * string, or fixing up a region according to user instructions. Instances of this class map 1:1
 * with FixupTask instances in the Agent.
 */
abstract class FixupSession(
    val controller: FixupService,
    val project: Project,
    var editor: Editor
) : Disposable {
  private val logger = Logger.getInstance(FixupSession::class.java)
  private val fixupService = FixupService.getInstance(project)
  private val documentListener by lazy { FixupSessionDocumentListener(this) }

  private val document
    get() = editor.document

  // taskId is passed back by the Agent when we initiate the editing task.
  @Volatile var taskId: String? = null

  // Add a public edits property
  var edits: List<TextEdit> = emptyList()

  // The current lens group. Changes as the state machine proceeds.
  @VisibleForTesting var diffLensGroup: LensWidgetGroup? = null
  private val lensGroups = mutableListOf<LensWidgetGroup>()

  @VisibleForTesting var selectionRange: RangeMarker? = null

  // Whether the session has inserted text into the document.
  val isInserted: Boolean
    get() = performedActions.any { it is InsertUndoableAction }

  // The prompt that the Agent used for this task. For Edit, it's the same as
  // the most recent prompt the user sent, which we already have. But for Document Code,
  // it enables us to show the user what we sent and let them hand-edit it.
  private var instruction: String? = null

  private val performedActions: MutableList<FixupUndoableAction> = mutableListOf()

  private val cancellationToken = CancellationToken()

  private val completionFuture: CompletableFuture<Void> =
      cancellationToken.onFinished {
        try {
          controller.clearActiveSession()
        } catch (x: Exception) {
          logger.debug("Session cleanup error", x)
        }
      }

  init {
    document.addDocumentListener(documentListener, /* parentDisposable= */ this)
    Disposer.register(controller, this)
    triggerFixupAsync()
  }

  @RequiresEdt
  private fun triggerFixupAsync() {
    // Those lookups require us to be on the EDT.
    val file = FileDocumentManager.getInstance().getFile(document)
    val textFile = file?.let { ProtocolTextDocument.fromVirtualFile(editor, it) } ?: return

    CodyAgentService.withAgent(project) { agent ->
      workAroundUninitializedCodebase()

      try {
        fixupService.startNewSession(this)
        // Spend a turn to get folding ranges before showing lenses.
        ensureSelectionRange(agent, textFile)
        showWorkingGroup()

        makeEditingRequest(agent)
            .handle { result, error ->
              if (error != null || result == null) {
                showErrorGroup("Error while generating doc string: $error")
              } else {
                // Handle multiple edits
                selectionRange = adjustToDocumentRange(result.selectionRange)
                edits = result.edits
              }
              null
            }
            .exceptionally { error: Throwable? ->
              if (!(error is CancellationException || error is CompletionException)) {
                showErrorGroup("Error while generating code: ${error?.localizedMessage}")
              }
              cancel()
              null
            }
            .completeOnTimeout(null, 3, TimeUnit.SECONDS)
      } catch (e: Exception) {
        showErrorGroup("Edit failed: ${e.localizedMessage}")
        cancel()
      }
    }
  }

  private fun adjustPositionForCodeLenses(position: Position?): Position {
    if (position == null) {
      throw IllegalArgumentException("Position cannot be null")
    }
    // Count the number of code lenses (edits) above the position
    val lensesAbove = edits.count { it.position?.line ?: 0 < position.line }

    // Adjust the position
    return Position(position.line + lensesAbove, position.character)
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
    selectionRange = selection.toRangeMarker(document, true)

    try {
      agent.server
          .getFoldingRanges(GetFoldingRangeParams(uri = textFile.uri, range = selection))
          .thenApply { result ->
            selectionRange = result.range.toRangeMarker(document, true)
            publishProgress(CodyInlineEditActionNotifier.TOPIC_FOLDING_RANGES)
            result
          }
          .get()
    } catch (e: Exception) {
      logger.warn("Error getting folding range", e)
    }
  }

  fun update(task: EditTask) {
    task.instruction?.let { instruction = it }

    when (task.state) {
      // This is an internal state (parked/ready tasks) and we should never see it.
      CodyTaskState.Idle -> {}
      // These four may or may not all arrive, depending on the operation, testing, etc.
      // They are all sent in quick succession and any one can substitute for another.
      CodyTaskState.Working,
      CodyTaskState.Inserting,
      CodyTaskState.Applying,
      CodyTaskState.Formatting -> {
        taskId = task.id
      }
      // Tasks remain in this state until explicit accept/undo/cancel.
      CodyTaskState.Applied -> {
        showBlockGroups()
        showDiffGroup()
      }
      // Then they transition to finished.
      CodyTaskState.Finished -> dispose()
      CodyTaskState.Error -> dispose()
      CodyTaskState.Pending -> {}
    }
  }

  // N.B. Blocks calling thread until the lens group is shown,
  // which may require switching to the EDT. This is primarily to help smooth
  // integration testing, but also because there's no real harm blocking pool threads.
  @RequiresEdt
  private fun showDiffLensGroup(group: LensWidgetGroup) {
    try {
      diffLensGroup?.let { if (!it.isDisposed.get()) Disposer.dispose(it) }
    } catch (x: Exception) {
      logger.warn("Error disposing previous lens group", x)
    }

    // Get the range for the lens based on total selection range
    diffLensGroup = group
    var range =
      selectionRange?.let {
        val position = Position(Range.fromRangeMarker(it).start.line, character = 0)
        Range(start = position, end = position)
      }

    if (range == null) {
      // Be defensive, as the protocol has been fragile with respect to selection ranges.
      logger.warn("No selection range for session: $this")
      // Last-ditch effort to show it somewhere other than top of file.
      val position = Position(editor.caretModel.currentCaret.logicalPosition.line, 0)
      range = Range(start = position, end = position)
    }

    val future = group.show(range)

    // Make sure the lens is visible.
    ApplicationManager.getApplication().invokeLater {
      if (!editor.isDisposed) {
        val logicalPosition = range.start.toLogicalPosition(editor.document)
        editor.scrollingModel.scrollTo(logicalPosition, ScrollType.CENTER)
      }
    }
    if (!ApplicationManager.getApplication().isDispatchThread) { // integration test
      future.get()
    }

    controller.notifySessionStateChanged()
  }

  @RequiresEdt
  private fun showBlockLensGroup(group: LensWidgetGroup, position: Position?) {
    if (position == null) {
      throw IllegalArgumentException("Position cannot be null")
    }

    logger.warn("JM: showing block lens at position: $position")

    // Add the new lens group to the list
    lensGroups.add(group)

    val future = group.show(Range(start = position, end = position))

    if (!ApplicationManager.getApplication().isDispatchThread) { // integration test
      future.get()
    }

    controller.notifySessionStateChanged()
  }

  // An optimization to avoid recomputing widget indentation in a tight loop.
  fun resetLensGroup() {
    diffLensGroup?.reset()
  }

  //Todo: JM. I believe this will need some reworking
  private fun showWorkingGroup() = runInEdt {
    showDiffLensGroup(LensGroupFactory(this).createTaskWorkingGroup())
    documentListener.setDiffLensGroupShown(false)
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
  }

  // Create an abstract val for command name
  abstract val commandName: String

  private fun showDiffGroup() = runInEdt {
    val isUnitTestCommand = commandName == "Test"

    showDiffLensGroup(LensGroupFactory(this).createDiffGroup(isUnitTestCommand))
    postAcceptActions()
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_DIFF_GROUP)
  }

  fun showBlockGroups() = runInEdt {
    logger.warn("JM: showBlockGroups() called")

    // Iterate over the edits and create block groups
    edits.forEach { edit ->
      logger.warn("JM: showBlockGroups() position: ${edit.position}")

      edit.position = adjustPositionForCodeLenses(edit.position)
      // Translate the edit's position for each to consider the space taken by previous code lenses
//      edits = edits.map { e ->
//        if (e.position == null){
//          logger.warn("JM: showBlockGroups() e.position is null")
//          //continue
//          return@map e
//        }
//        val adjustedPosition = adjustPositionForCodeLenses(e.position, edits)
//        logger.warn("Position modified from ${e.position} to $adjustedPosition")
//        e.copy(position = adjustedPosition)
//      }

      showBlockLensGroup(LensGroupFactory(this).createBlockGroup(edit.id), edit.position)
      publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_BLOCK_GROUP)
    }
  }


  fun showErrorGroup(hoverText: String) = runInEdt {
    showDiffLensGroup(LensGroupFactory(this).createErrorGroup(hoverText))
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ERROR_GROUP)
  }

  /** Subclass sends a fixup command to the agent, and returns the initial task. */
  abstract fun makeEditingRequest(agent: CodyAgent): CompletableFuture<EditTask>

  fun acceptAll() {
    try {
      // Avoid race conditions by ensuring that we flag Accept as underway early on,
      // and no matter which code path gets us here. This call should be idempotent.
      // I saw the race condition in a debugger, which exacerbated the race window.
      postAcceptActions()

      CodyAgentService.withAgent(project) { agent ->
        agent.server.acceptAllEditTask(TaskIdParam(taskId!!))
        publishProgress(CodyInlineEditActionNotifier.TOPIC_PERFORM_ACCEPT_ALL)
      }
    } catch (x: Exception) {
      // Don't show error lens here; it's sort of pointless.
      logger.warn("Error sending editTask/acceptAll for taskId", x)
      dispose()
    }
  }

  fun accept(editId: String) {
    try{
      postAcceptActions() //Todo: Jm. maybe need a postAcceptActions?

      CodyAgentService.withAgent(project) { agent ->
        //logger.warn("JM: sending accept() range: $range and taskId: $taskId")
        //agent.server.acceptEditTask(TaskIdParam(taskId!!, range!!)) Todo: Jm. will likely need to reinstate this

        // Remove the corresponding edit from the list
        removeEditFromList(editId)

        // If the code lens still renders, I need to remove it here

        publishProgress(CodyInlineEditActionNotifier.TOPIC_PERFORM_ACCEPT)
      }
    } catch (x: Exception) {
      // Don't show error lens here; it's sort of pointless.
      logger.warn("Error sending editTask/accept for taskId", x)
      dispose()
    }
  }

  fun removeEditFromList(editId: String) {
    // Create a new list excluding the edit with the specified position
    val beforeLength = edits.size
    edits = edits.filterNot { edit ->
      edit.id == editId
    }
    logger.warn("JM: edits length before: $beforeLength, after: ${edits.size}")
  }

  fun reject(editId: String) {
    try{
      postAcceptActions() //Todo: Jm. maybe need a postRejectactions?

      CodyAgentService.withAgent(project) { agent ->
        //agent.server.rejectEditTask(TaskIdParam(taskId!!, range!!)) //Todo: Jm. will likely need to reinstate this

        // Remove the corresponding edit from the list
        removeEditFromList(editId)

        publishProgress(CodyInlineEditActionNotifier.TOPIC_PERFORM_REJECT)
      }
    } catch (x: Exception) {
      // Don't show error lens here; it's sort of pointless.
      logger.warn("Error sending editTask/reject for taskId", x)
      dispose()
    }
  }

  private fun postAcceptActions() {
    // This is the specific moment after which any edits to the document,
    // including edits generated by the Agent, will result in an auto-accept.
    //
    // It's important to get the timing right here, and do it before any async
    // edits (from workspace/edit requests) or user edits arrive, or we will
    // accidentally auto-accept the suggested edit.
    documentListener.setDiffLensGroupShown(true)
    documentListener.setBlockLensGroupShown(true)
    EditCommandPrompt.clearLastPrompt()
  }

  fun cancel() {
    if (taskId == null) {
      dispose()
    } else {
      try {
        CodyAgentService.withAgent(project) { agent ->
          agent.server.cancelEditTask(TaskIdParam(taskId!!))
        }
      } catch (x: Exception) {
        // Error lens here is counterproductive as well.
        logger.warn("Error sending editTask/cancel for taskId: ${x.localizedMessage}")
        dispose()
      }
    }
  }

  fun undo() {
    showWorkingGroup()
    CodyAgentService.withAgentRestartIfNeeded(
        project,
        callback = { agent: CodyAgent ->
          agent.server.undoEditTask(TaskIdParam(taskId!!))
          publishProgress(CodyInlineEditActionNotifier.TOPIC_PERFORM_UNDO)
        },
        onFailure = { exception ->
          showErrorGroup("Error sending editTask/undo for taskId: ${exception.localizedMessage}")
        })
  }

  fun showRetryPrompt() {
    runInEdt {
      // Close loophole where you can keep retrying after the ignore policy changes.
      if (controller.isEligibleForInlineEdit(editor)) {
        EditCommandPrompt(controller, editor, "Edit instructions and Retry", instruction)
      }
    }
  }

  fun afterSessionFinished(action: Runnable) {
    completionFuture.thenRun(action)
  }

  fun performWorkspaceEdit(workspaceEditParams: WorkspaceEditParams) {

    for (op in workspaceEditParams.operations) {

      op.uri?.let { updateEditorIfNeeded(it) }

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
            logger.info("Applying edits to a file (size ${document.textLength} chars): ${op.uri}")
            performInlineEdits(op.edits)
          }
        }
        else ->
            logger.warn(
                "DocumentCommand session received unknown workspace edit operation: ${op.type}")
      }
    }
    publishProgress(CodyInlineEditActionNotifier.TOPIC_WORKSPACE_EDIT)
  }

  internal fun updateEditorIfNeeded(path: String) {
    val vf =
        CodyEditorUtil.findFileOrScratch(project, path) ?: throw CodyEditingNotAvailableException()

    val documentForFile = FileDocumentManager.getInstance().getDocument(vf)

    if (document != documentForFile) {
      document.removeDocumentListener(documentListener)
      CodyEditorUtil.getAllOpenEditors()
          .firstOrNull { it.document == documentForFile }
          ?.let { newEditor -> editor = newEditor }

      val textFile = ProtocolTextDocument.fromVirtualFile(editor, vf)
      CodyAgentService.withAgent(project) { agent ->
        ensureSelectionRange(agent, textFile)
        document.addDocumentListener(documentListener, /* parentDisposable= */ this)
        showWorkingGroup()
      }
    }
  }

  fun performInlineEdits(edits: List<TextEdit>) {
    try {
      diffLensGroup?.withListenersMuted {
        if (!controller.isEligibleForInlineEdit(editor)) {
          return@withListenersMuted logger.warn("Inline edit not eligible")
        }

        WriteCommandAction.runWriteCommandAction(project) {
          val currentActions =
              edits.mapNotNull { edit ->
                try {
                  when (edit.type) {
                    "replace",
                    "delete" -> ReplaceUndoableAction(project, edit, document)
                    "insert" -> InsertUndoableAction(project, edit, document)
                    else -> {
                      logger.warn("Unknown edit type: ${edit.type}")
                      null
                    }
                  }
                } catch (e: RuntimeException) {
                  throw EditCreationException(edit, e)
                }
              }

          currentActions.forEach { action ->
            try {
              action.apply()
            } catch (e: RuntimeException) {
              throw EditExecutionException(action, e)
            }
          }

          performedActions += currentActions
        }
      }
    } finally {
      publishProgress(CodyInlineEditActionNotifier.TOPIC_TEXT_DOCUMENT_EDIT)
    }
  }

  private fun adjustToDocumentRange(r: Range): RangeMarker {
    // Negative values of the start/end line are used to mark beginning/end of the document
    val start = if (r.start.line < 0) Position(line = 0, character = r.start.character) else r.start
    val endLine = document.getLineNumber(document.textLength)
    val endLineLength = document.getLineEndOffset(endLine) - document.getLineStartOffset(endLine)
    val end = if (r.end.line < 0) Position(line = endLine, character = endLineLength) else r.end
    return Range(start, end).toRangeMarker(document, true)
  }

  fun createDiffDocument(): Document {
    val document = EditorFactory.getInstance().createDocument(document.text)
    val diffActions = performedActions.map { it.copyForDocument(document) }
    WriteCommandAction.runWriteCommandAction(project) {
      diffActions.reversed().forEach { it.undo() }
    }
    return document
  }

  override fun dispose() {
    if (project.isDisposed) return
    performedActions.forEach { it.dispose() }
    diffLensGroup?.dispose()
    lensGroups.forEach { group ->
      try {
        if (!group.isDisposed.get()) Disposer.dispose(group)
      } catch (x: Exception) {
        logger.warn("Error disposing lens group", x)
      }
    }
    lensGroups.clear()
    cancellationToken.dispose()
    publishProgress(CodyInlineEditActionNotifier.TOPIC_TASK_FINISHED)
  }

  fun isShowingWorkingLens(): Boolean {
    return diffLensGroup?.isInWorkingGroup == true
  }

  /** Returns true if the Accept lens group is currently active. */
  fun isShowingDiffLens(): Boolean {
    return diffLensGroup?.isDiffGroup == true
  }

  fun isShowingBlockLens(): Boolean {
    return diffLensGroup?.isBlockGroup == true //TOdo: JM - needs to use correct lens group
  }

  fun isShowingErrorLens(): Boolean {
    return diffLensGroup?.isErrorGroup == true
  }

  fun hasDiffLensBeenShown(): Boolean {
    return documentListener.isDiffLensGroupShown.get()
  }

  private fun publishProgress(topic: Topic<CodyInlineEditActionNotifier>) {
    ApplicationManager.getApplication().invokeLater {
      project.messageBus.syncPublisher(topic).afterAction()
    }
  }

  override fun toString(): String {
    val file = FileDocumentManager.getInstance().getFile(editor.document)
    return "${this::javaClass.name} for ${file?.path ?: "unknown file"}"
  }

  companion object {
    // JetBrains Actions that we fire when the lenses are clicked.
    const val ACTION_ACCEPT_ALL = "cody.inlineEditAcceptAllAction"
    const val ACTION_ACCEPT = "cody.inlineEditAcceptAction"
    const val ACTION_REJECT = "cody.inlineEditRejectAction"
    const val ACTION_CANCEL = "cody.inlineEditCancelAction"
    const val ACTION_RETRY = "cody.inlineEditRetryAction"
    const val ACTION_DIFF = "cody.editShowDiffAction"
    const val ACTION_UNDO = "cody.inlineEditUndoAction"
    const val ACTION_DISMISS = "cody.inlineEditDismissAction"
  }
}
