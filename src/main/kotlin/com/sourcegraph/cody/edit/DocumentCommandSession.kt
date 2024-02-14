package com.sourcegraph.cody.edit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.CodyTaskState
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.isTerminal
import com.sourcegraph.cody.edit.InlineFixups.Companion.backgroundThread
import com.sourcegraph.cody.vscode.CancellationToken
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DocumentCommandSession(editor: Editor, cancellationToken: CancellationToken) :
    InlineFixupCommandSession(editor, cancellationToken) {
  private val logger = Logger.getInstance(DocumentCommandSession::class.java)

  init {
    triggerDocumentCodeAsync()
  }

  // TODO: Refactor this boilerplate into a utility class that generates all this stuff.
  private fun triggerDocumentCodeAsync(): CompletableFuture<Void?> {
    val project = editor.project!!
    val asyncRequest = CompletableFuture<Void?>()
    cancellationToken.onCancellationRequested { asyncRequest.cancel(true) }

    CodyAgentService.withAgent(project).thenAccept { agent ->
      workAroundUninitializedCodebase(editor)
      addClientListeners(editor, agent)

      val response = agent.server.commandsDocument()
      cancellationToken.onCancellationRequested { response.cancel(true) }

      backgroundThread {
        response
            .handle { result, error ->
              if (error != null || result == null) {
                logger.warn("Error while generating doc string: $error")
              } else {
                beginTrackingTask(editor, result)
              }
              null
            }
            .exceptionally { error: Throwable? ->
              logger.warn("Error while generating doc string: $error")
              null
            }
            .completeOnTimeout(null, 3, TimeUnit.SECONDS)
            .thenRun {
              logger.warn("DEBUG: ================ Completed request: $response")
              asyncRequest.complete(null)
            }
      }
    }

    return asyncRequest
  }

  // We're consistently triggering the 'retrieved codebase context before initialization' error
  // in ContextProvider.ts. It's a different initialization path from completions & chat.
  // Calling onFileOpened forces the right initialization path.
  private fun workAroundUninitializedCodebase(editor: Editor) {
    val file = FileDocumentManager.getInstance().getFile(editor.document)!!
    val project = editor.project!!
    CodyAgentCodebase.getInstance(project).onFileOpened(project, file)
  }

  private fun beginTrackingTask(editor: Editor, task: EditTask) {
    taskId = task.id
    CodyAgentService.withAgent(editor.project!!).thenAccept { agent ->
      addClientListeners(editor, agent)
    }
  }

  private fun addClientListeners(editor: Editor, agent: CodyAgent) {

    agent.client.setOnEditTaskStateDidChange { task ->
      if (task.id != taskId) return@setOnEditTaskStateDidChange
      if (task.state.isTerminal) {
        cancellationToken.abort() // TODO: necessary?
        // If we're finished, we close up shop for this listener,
        // and wait for the editing notification to arrive.
        if (task.state == CodyTaskState.finished) {
          logger.warn("Finished task $taskId")
          // TODO: Remove progress indicator
        } else {
          logger.warn("TODO: Handle error case")
        }
      } else {
        logger.warn("Progress update for task $taskId: ${task.state}")
      }
    }

    agent.client.setOnDisplayCodeLens { params ->
      if (params.uri != FileDocumentManager.getInstance().getFile(editor.document)?.url) {
        logger.warn("received code lens for wrong document: ${params.uri}")
        return@setOnDisplayCodeLens
      }
      InlineCodeLenses.get(editor).apply {
        ApplicationManager.getApplication().invokeLater { this.display(params) }
      }
    }

    // TODO: This seems like it might be deprecated soon.
    agent.client.setOnTextDocumentEdit { params ->
      if (params.uri != FileDocumentManager.getInstance().getFile(editor.document)?.path) {
        logger.warn("received notification for wrong document: ${params.uri}")
      } else {
        performInlineEdits(params.edits)
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
              performInlineEdits(op.edits)
            }
          }
          else -> {
            logger.warn(
                "DocumentCommand session received unknown workspace edit operation: ${op.type}")
          }
        }
      }
    }
  }

  override fun cancel() {
    logger.warn("Cancelling DocumentCommandSession -- TODO")
  }

  override fun getLogger() = logger
}
