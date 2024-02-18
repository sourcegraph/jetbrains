package com.sourcegraph.cody.edit

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService.Companion.withAgent
import com.sourcegraph.cody.edit.FixupService.Companion.backgroundThread
import com.sourcegraph.cody.edit.widget.LensWidgetGroup
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

class DocumentCodeSession(editor: Editor) : FixupSession(editor) {
  private val logger = Logger.getInstance(DocumentCodeSession::class.java)
  private val project = editor.project!!

  private var lensGroup: LensWidgetGroup? = null

  private val lensActionCallbacks =
      mapOf(
          "cody.fixup.codelens.accept" to { accept() },
          "cody.fixup.codelens.cancel" to { cancel() },
          "cody.fixup.codelens.retry" to { retry() },
          "cody.fixup.codelens.diff" to { showDiff() },
          "cody.fixup.codelens.undo" to { undo() },
      )

  init {
    triggerDocumentCodeAsync()
  }

  override fun getLogger() = logger

  private fun triggerDocumentCodeAsync(): CompletableFuture<Void?> {
    val resultOuter = CompletableFuture<Void?>()
    currentJob.get().onCancellationRequested { resultOuter.cancel(true) }

    withAgent(project) { agent ->
      workAroundUninitializedCodebase(editor)
      addClientListeners(agent)
      val response = agent.server.commandsDocument()
      currentJob.get().onCancellationRequested { response.cancel(true) }

      backgroundThread {
        response
            .handle { result, error ->
              if (error != null || result == null) {
                // TODO: Adapt logic from CodyCompletionsManager.handleError
                logger.warn("Error while generating doc string: $error")
              } else {
                taskId = result.id
              }
              null
            }
            .exceptionally { error: Throwable? ->
              if (!(error is CancellationException || error is CompletionException)) {
                logger.warn("Error while generating doc string: $error")
              }
              null
            }
            .completeOnTimeout(null, 3, TimeUnit.SECONDS)
            .thenRun { resultOuter.complete(null) }
      }
    }
    return resultOuter
  }

  // We're consistently triggering the 'retrieved codebase context before initialization' error
  // in ContextProvider.ts. It's a different initialization path from completions & chat.
  // Calling onFileOpened forces the right initialization path.
  private fun workAroundUninitializedCodebase(editor: Editor) {
    val file = FileDocumentManager.getInstance().getFile(editor.document)!!
    CodyAgentCodebase.getInstance(project).onFileOpened(project, file)
  }

  @RequiresEdt
  private fun addClientListeners(agent: CodyAgent) {
    agent.client.setOnDisplayCodeLens { params ->
      if (params.uri != FileDocumentManager.getInstance().getFile(editor.document)?.url) {
        logger.warn("received code lens for wrong document: ${params.uri}")
        return@setOnDisplayCodeLens
      }
      disposeLenses()
      LensWidgetGroup(this, editor).let {
        synchronized(this) {
          lensGroup = it // Set this first, in case of race conditions.
          it.display(params, lensActionCallbacks)
        }
      }
    }

    agent.client.setOnEditTaskStateDidChange { task ->
      if (task.id == taskId) {
        logger.warn("Task $taskId state change: ${task.state}") // TODO: Handle this.
      }
    }

    agent.client.setOnWorkspaceEdit { params ->
      for (op in params.operations) {
        when (op.type) {
          "create-file" -> logger.warn("Workspace edit operation created a file: ${op.uri}")
          "rename-file" ->
              logger.warn("Workspace edit operation renamed a file: ${op.oldUri} -> ${op.newUri}")
          "delete-file" -> logger.warn("Workspace edit operation deleted a file: ${op.uri}")
          "edit-file" -> {
            if (op.edits == null) {
              logger.warn("Workspace edit operation has no edits")
            } else {
              lensGroup!!.withListenersMuted { performInlineEdits(op.edits) }
            }
          }
          else ->
              logger.warn(
                  "DocumentCommand session received unknown workspace edit operation: ${op.type}")
        }
      }
    }

    // We get a textDocument/edit notification here with the doc comment to insert.
    // However, we also get a workspace/edit notification with the same edits.
    agent.client.setOnTextDocumentEdit { params ->
      logger.warn("DocumentCommand session received text document edit: $params")
    }
  }

  fun accept() {
    // TODO: Telemetry
    finish()
  }

  override fun cancel() {
    // TODO: Telemetry
    finish()
  }

  override fun retry() {
    // TODO: Telemetry
    FixupService.instance.documentCode(editor) // Disposes this session.
  }

  // Brings up a diff view showing the changes the AI made.
  private fun showDiff() {
    logger.warn("Code Lenses: Show Diff")
  }

  @RequiresEdt
  private fun disposeLenses() {
    lensGroup?.let { Disposer.dispose(it) }
  }

  fun undo() {
    finish()
    // TODO: Telemetry
    try {
      val undoManager = UndoManager.getInstance(editor.project!!)
      if (undoManager.isUndoAvailable(null)) {
        undoManager.undo(null)
      }
    } catch (t: Throwable) {
      logger.error("Error applying Undo", t)
    }
  }
}
