package com.sourcegraph.cody.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentCodebase
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument

class CodyFocusChangeListener : FocusChangeListener {

  override fun focusGained(editor: Editor) {
    val project = editor.project
    val document = ProtocolTextDocument.fromEditor(editor)
    val file = FileDocumentManager.getInstance().getFile(editor.document)

    if (project != null && document != null && file != null) {
      CodyAgentService.withAgent(project) { agent: CodyAgent ->
        agent.server.textDocumentDidFocus(document)
      }
      CodyAgentCodebase.getInstance(project).onFileOpened(file)
    }
  }
}
