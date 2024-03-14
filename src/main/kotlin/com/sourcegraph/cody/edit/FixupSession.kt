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
import com.sourcegraph.cody.agent.protocol.CodyTaskState
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.edit.widget.LensGroupFactory
import com.sourcegraph.cody.edit.widget.LensWidgetGroup

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

  private val lensActionCallbacks =
      mapOf(
          COMMAND_ACCEPT to { accept() },
          COMMAND_CANCEL to { cancel() },
          COMMAND_RETRY to { retry() },
          COMMAND_DIFF to { diff() },
          COMMAND_UNDO to { undo() },
      )

  abstract fun getLogger(): Logger

  fun commandCallbacks(): Map<String, () -> Unit> = lensActionCallbacks

  fun update(task: EditTask) {
    lensGroup?.let { if (!it.isDisposed.get()) Disposer.dispose(it) }
    logger.warn("Task updated: $task")
    when (task.state) {
        CodyTaskState.Idle -> {}
        CodyTaskState.Working -> {
            lensGroup = LensGroupFactory(this).createTaskWorkingGroup()
        }
        CodyTaskState.Inserting -> {}
        CodyTaskState.Applying -> {}
        CodyTaskState.Formatting -> {}
        CodyTaskState.Applied -> {
            lensGroup = LensGroupFactory(this).createAcceptGroup()
        }
        CodyTaskState.Finished -> {}
        CodyTaskState.Error -> {}
        CodyTaskState.Pending -> {}
    }
  }

  fun finish() {
    controller.removeSession(this)
    Disposer.dispose(this)
  }

  abstract fun accept()

  abstract fun retry()

  abstract fun cancel()

  abstract fun diff()

  abstract fun undo()

  fun performInlineEdits(edits: List<TextEdit>) {
    // TODO: This is an artifact of the update to concurrent editing tasks.
    // We do need to mute any LensGroup listeners, but this is an ugly way to do it.
    lensGroup?.withListenersMuted {
      if (!controller.isEligibleForInlineEdit(editor)) {
        return@withListenersMuted getLogger().warn("Inline edit not eligible")
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
            else -> getLogger().warn("Unknown edit type: ${edit.type}")
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
    doc.replaceString(start, end, edit.value ?: return)
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
