package com.sourcegraph.cody.autocomplete.action

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.protocol.AutocompleteItem
import com.sourcegraph.cody.autocomplete.AutocompleteText
import com.sourcegraph.cody.autocomplete.AutocompleteTextAtCaret
import com.sourcegraph.cody.autocomplete.CodyAutocompleteManager
import com.sourcegraph.telemetry.GraphQlLogger
import com.sourcegraph.utils.CodyEditorUtil

class AcceptAutocompleteActionHandler : AutocompleteActionHandler() {
  /**
   * Applies the autocomplete to the document at a caret. This replaces the string between the caret
   * offset and its line end with the autocompletion string and then moves the caret to the end of
   * the autocompletion.
   *
   * @param document the document to apply the autocomplete to
   * @param autoComplete the actual autocomplete text along with the corresponding caret
   */
  private fun applyAutocomplete(document: Document, autoComplete: AutocompleteTextAtCaret) {
    // Calculate the end of the line to replace
    val lineEndOffset = document.getLineEndOffset(document.getLineNumber(autoComplete.caret.offset))

    // Get autocompletion string
    val autoCompletionString =
        autoComplete.autoCompleteText.getAutoCompletionString(
            document.getText(TextRange.create(autoComplete.caret.offset, lineEndOffset)))

    // If the autocompletion string does not contain the suffix of the line, add it to the end
    val sameLineSuffix =
        document.getText(TextRange.create(autoComplete.caret.offset, lineEndOffset))
    val sameLineSuffixIfMissing =
        if (autoCompletionString.contains(sameLineSuffix)) "" else sameLineSuffix

    // Replace the line with the autocompletion string
    val finalAutoCompletionString = autoCompletionString + sameLineSuffixIfMissing
    document.replaceString(autoComplete.caret.offset, lineEndOffset, finalAutoCompletionString)

    // Move the caret to the end of the autocompletion string
    autoComplete.caret.moveToOffset(autoComplete.caret.offset + finalAutoCompletionString.length)
  }

  /**
   * Applies the autocomplete to the document at a caret: 1. Replaces the string between the caret
   * offset and its line end with the current completion 2. Moves the caret to the start and end
   * offsets with the completion text. If there are multiple carets, uses the first one. If there
   * are no completions at the caret, does nothing.
   */
  override fun doExecute(editor: Editor, maybeCaret: Caret?, dataContext: DataContext?) {
    val project = editor.project ?: return
    val server = CodyAgent.getServer(project)
    if (server != null) {
      val telemetry = CodyAutocompleteManager.instance.currentAutocompleteTelemetry ?: return

      val id = telemetry.logID
      if (id != null) {
        server.completionsAccepted(id)
      }

      // GraphQlLogger.logAutocompleteAcceptedEvent(project, telemetry?.params())
      server.autocompleteClearLastCandidate()
      acceptAgentAutocomplete(editor, maybeCaret)
    } else {
      val caret = maybeCaret ?: getSingleCaret(editor) ?: return
      AutocompleteText.atCaret(caret)?.let {
        /* Log the event */
        GraphQlLogger.logCodyEvent(project, "completion", "accepted")
        WriteAction.run<RuntimeException> { applyAutocomplete(editor.document, it) }
      }
    }
  }

  private fun acceptAgentAutocomplete(editor: Editor, maybeCaret: Caret?) {
    val caret = maybeCaret ?: getSingleCaret(editor) ?: return
    val completionItem = getCurrentAutocompleteItem(caret) ?: return
    WriteAction.run<RuntimeException> { applyInsertText(editor, caret, completionItem) }
  }

  companion object {

    private fun applyInsertText(editor: Editor, caret: Caret, completionItem: AutocompleteItem) {
      val document = editor.document
      val range = CodyEditorUtil.getTextRange(document, completionItem.range)
      document.replaceString(range.startOffset, range.endOffset, completionItem.insertText)
      caret.moveToOffset(range.startOffset + completionItem.insertText.length)
      editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
  }
}
