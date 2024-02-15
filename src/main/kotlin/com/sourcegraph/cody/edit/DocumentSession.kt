package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService.Companion.withAgent
import com.sourcegraph.cody.agent.protocol.CodyTaskState
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.isTerminal
import com.sourcegraph.cody.edit.InlineFixups.Companion.backgroundThread
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

class DocumentSession(editor: Editor) : InlineFixupSession(editor) {
  private val logger = Logger.getInstance(DocumentSession::class.java)
  val project = editor.project!!

  init {
    triggerDocumentCodeAsync()
  }

  private fun triggerDocumentCodeAsync(): CompletableFuture<Void?> {
    val resultOuter = CompletableFuture<Void?>()
    currentJob.get().onCancellationRequested { resultOuter.cancel(true) }

    withAgent(project) { agent ->
      workAroundUninitializedCodebase(editor)
      addClientListeners(editor, agent)

      val response = agent.server.commandsDocument()
      currentJob.get().onCancellationRequested { response.cancel(true) }

      backgroundThread {
        response
            .handle { result, error ->
              if (error != null || result == null) {
                // TODO: Adapt logic from CodyCompletionsManager.handleError
                logger.warn("Error while generating doc string: $error")
              } else {
                beginTrackingTask(editor, result)
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

  private fun beginTrackingTask(editor: Editor, task: EditTask) {
    taskId = task.id
    withAgent(project) { agent -> addClientListeners(editor, agent) }
  }

  private fun addClientListeners(editor: Editor, agent: CodyAgent) {

    agent.client.setOnEditTaskStateDidChange { task ->
      if (task.id != taskId) return@setOnEditTaskStateDidChange
      if (task.state.isTerminal) {
        if (task.state == CodyTaskState.finished) {
          logger.warn("Finished task $taskId")
        } else {
          logger.warn("TODO: Handle error terminal case")
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
      InlineCodeLenses(this, editor).apply { backgroundThread { display(params) } }
    }

    // TODO: We don't get these for commands/document.
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
          else ->
              logger.warn(
                  "DocumentCommand session received unknown workspace edit operation: ${op.type}")
        }
      }
    }
  }

  override fun getLogger() = logger
}
