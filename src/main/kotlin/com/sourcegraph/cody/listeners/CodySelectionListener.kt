package com.sourcegraph.cody.listeners

import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument
import com.sourcegraph.cody.autocomplete.CodyAutocompleteManager
import com.sourcegraph.config.ConfigUtil

class CodySelectionListener : SelectionListener {
  override fun selectionChanged(e: SelectionEvent) {
    if (!ConfigUtil.isCodyEnabled()) {
      return
    }

    val editor = e.editor
    val project = editor.project
    val file = FileDocumentManager.getInstance().getFile(editor.document)

    if (project != null && file != null) {
      ProtocolTextDocument.fromEditor(editor)?.let { textEditor ->
        CodyAgentService.withAgent(project) { agent ->
          agent.server.textDocumentDidFocus(textEditor)
        }
      }
    }

    CodyAutocompleteManager.instance.clearAutocompleteSuggestions(editor)
  }
}
