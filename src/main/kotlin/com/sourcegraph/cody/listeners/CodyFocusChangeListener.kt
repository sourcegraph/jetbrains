package com.sourcegraph.cody.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol_extensions.fromEditor
import com.sourcegraph.cody.agent.protocol_generated.TextDocument_DidFocusParams
import com.sourcegraph.cody.ignore.IgnoreOracle

class CodyFocusChangeListener(val project: Project) : FocusChangeListener {

  override fun focusGained(editor: Editor) {
    fromEditor(editor)?.let { textDocument ->
      EditorChangesBus.documentChanged(project, textDocument)
      CodyAgentService.withAgent(project) { agent: CodyAgent ->
        agent.server.textDocument_didFocus(TextDocument_DidFocusParams(textDocument.uri))
      }
      IgnoreOracle.getInstance(project).focusedFileDidChange(textDocument.uri)
    }
  }
}
