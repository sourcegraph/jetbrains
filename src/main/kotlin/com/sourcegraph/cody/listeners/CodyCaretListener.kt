package com.sourcegraph.cody.listeners

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import com.sourcegraph.cody.autocomplete.CodyAutocompleteManager
import com.sourcegraph.cody.vscode.InlineCompletionTriggerKind
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.utils.CodyEditorUtil

class CodyCaretListener(val project: Project) : CaretListener {
  override fun caretPositionChanged(e: CaretEvent) {
    if (!ConfigUtil.isCodyEnabled()) {
      return
    }

    val commandName = CommandProcessor.getInstance().currentCommandName
    if (commandName == CodyEditorUtil.VIM_EXIT_INSERT_MODE_ACTION) {
      return
    }

    ProtocolTextDocument.fromEditor(e.editor)?.let { textDocument ->
      CodyAgentService.withAgent(project) { agent: CodyAgent ->
        agent.server.textDocumentDidChange(textDocument)
      }
    }

    CodyAutocompleteManager.instance.clearAutocompleteSuggestions(e.editor)
    CodyAutocompleteManager.instance.triggerAutocomplete(
        e.editor, e.editor.caretModel.offset, InlineCompletionTriggerKind.AUTOMATIC)
  }
}
