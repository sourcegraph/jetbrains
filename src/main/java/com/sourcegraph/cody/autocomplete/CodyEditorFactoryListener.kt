package com.sourcegraph.cody.autocomplete

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.util.Disposer
import com.sourcegraph.cody.agent.CodyAgent.Companion.getClient
import com.sourcegraph.cody.agent.CodyAgent.Companion.isConnected
import com.sourcegraph.cody.agent.protocol.Position
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.agent.protocol.TextDocument
import com.sourcegraph.cody.autocomplete.CodyAutocompleteManager.Companion.instance
import com.sourcegraph.cody.vscode.InlineAutocompleteTriggerKind
import com.sourcegraph.cody.vscode.InlineCompletionTriggerKind
import com.sourcegraph.config.ConfigUtil.isCodyEnabled
import com.sourcegraph.utils.CodyEditorUtil.VIM_EXIT_INSERT_MODE_ACTION
import com.sourcegraph.utils.CodyEditorUtil.isEditorValidForAutocomplete
import com.sourcegraph.utils.CodyEditorUtil.isImplicitAutocompleteEnabledForEditor

/**
 * Determines when to trigger completions and when to clear completions.
 *
 * IntelliJ doesn't have a built-in API to register "inline completion providers" similar to VS
 * Code. Instead, we manually listen to editor events like the caret position, selection changes,
 * and document edits.
 */
class CodyEditorFactoryListener : EditorFactoryListener {
  private var selectionListener = CodySelectionListener()
  private var caretListener: CaretListener = CodyCaretListener()

  override fun editorCreated(event: EditorFactoryEvent) {
    if (!isCodyEnabled()) {
      return
    }
    val editor = event.editor
    informAgentAboutEditorChange(editor, skipCodebaseOnFileOpened=false)
    val project = editor.project
    if (project == null || project.isDisposed) {
      return
    }
    val disposable = Disposer.newDisposable("CodyEditorFactoryListener")
    EditorUtil.disposeWithEditor(editor, disposable)
    editor.caretModel.addCaretListener(caretListener, disposable)
    editor.selectionModel.addSelectionListener(selectionListener, disposable)
    editor.document.addDocumentListener(CodyDocumentListener(editor), disposable)
  }

  private class CodyCaretListener : CaretListener {
    override fun caretPositionChanged(e: CaretEvent) {
      if (!isCodyEnabled()) {
        return
      }
      val commandName = CommandProcessor.getInstance().currentCommandName
      if (commandName == VIM_EXIT_INSERT_MODE_ACTION) {
        return
      }
      informAgentAboutEditorChange(e.editor)
      val suggestions = instance
      val editor = e.editor
      if (isEditorValidForAutocomplete(editor) && isSelectedEditor(editor)) {
        suggestions.clearAutocompleteSuggestions(e.editor)
        if (isImplicitAutocompleteEnabledForEditor(editor))
            suggestions.triggerAutocomplete(
                e.editor, e.editor.caretModel.offset, InlineCompletionTriggerKind.AUTOMATIC)
      }
    }
  }

  private class CodySelectionListener : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      if (!isCodyEnabled()) {
        return
      }
      val editor = e.editor
      informAgentAboutEditorChange(editor)
      if (isEditorValidForAutocomplete(editor) && isCodyEnabled() && isSelectedEditor(editor))
          instance.clearAutocompleteSuggestions(editor)
    }
  }

  private class CodyDocumentListener(private val editor: Editor) : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(event: DocumentEvent) {
      if (!isSelectedEditor(editor)) {
        return
      }
      val completions = instance
      completions.clearAutocompleteSuggestions(editor)
      if (isImplicitAutocompleteEnabledForEditor(editor) &&
          isEditorValidForAutocomplete(editor) &&
          !CommandProcessor.getInstance().isUndoTransparentActionInProgress) {
        informAgentAboutEditorChange(editor)
        val changeOffset = event.offset + event.newLength
        if (editor.caretModel.offset == changeOffset) {
          val requestType =
              if (event.oldLength != event.newLength) InlineAutocompleteTriggerKind.Invoke
              else InlineAutocompleteTriggerKind.Automatic
          completions.triggerAutocomplete(
              editor, changeOffset, InlineCompletionTriggerKind.AUTOMATIC)
        }
      }
    }
  }

  companion object {
    /**
     * Returns true if this editor is currently open and focused by the user. Returns true if this
     * editor is in a separate tab or not focused/selected by the user.
     */
    private fun isSelectedEditor(editor: Editor?): Boolean {
      if (editor == null) {
        return false
      }
      val project = editor.project
      if (project == null || project.isDisposed) {
        return false
      }
      val editorManager = FileEditorManager.getInstance(project) ?: return false
      if (editorManager is FileEditorManagerImpl) {
        val current = editorManager.getSelectedTextEditor(true)
        return current != null && current == editor
      }
      val current = editorManager.getSelectedEditor()
      return current is TextEditor && editor == current.editor
    }

    private fun getSelection(editor: Editor): Range? {
      val selectionModel = editor.selectionModel
      val selectionStartPosition = selectionModel.selectionStartPosition
      val selectionEndPosition = selectionModel.selectionEndPosition
      if (selectionStartPosition != null && selectionEndPosition != null) {
        return Range()
            .setStart(
                Position()
                    .setLine(selectionStartPosition.line)
                    .setCharacter(selectionStartPosition.column))
            .setEnd(
                Position()
                    .setLine(selectionEndPosition.line)
                    .setCharacter(selectionEndPosition.column))
      }
      val carets = editor.caretModel.allCarets
      if (!carets.isEmpty()) {
        val caret = carets[0]
        val position =
            Position()
                .setLine(caret.logicalPosition.line)
                .setCharacter(caret.logicalPosition.column)
        // A single-offset caret is a selection where end == start.
        return Range().setStart(position).setEnd(position)
      }
      return null
    }

    // Sends a textDocument/didChange notification to the agent server.
    fun informAgentAboutEditorChange(editor: Editor?, skipCodebaseOnFileOpened: Boolean = true) {
      if (editor == null) {
        return
      }
      if (editor.project == null) {
        return
      }
      if (!isConnected(editor.project!!)) {
        return
      }
      val client = getClient(editor.project!!)
      if (client.server == null) {
        return
      }
      val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
      val document =
          TextDocument()
              .setFilePath(file.path)
              .setContent(editor.document.text)
              .setSelection(getSelection(editor))
      client.server!!.textDocumentDidChange(document)
      if (client.codebase == null || skipCodebaseOnFileOpened) {
        return
      }
      client.codebase!!.onFileOpened(editor.project!!, file)
    }
  }
}
