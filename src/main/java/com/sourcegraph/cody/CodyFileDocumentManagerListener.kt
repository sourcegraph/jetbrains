package com.sourcegraph.cody

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument

class CodyFileDocumentManagerListener(val project: Project) : FileDocumentManagerListener {

  override fun beforeDocumentSaving(document: Document) {
    CodyAgentService.withAgent(project).thenAccept { agent ->
      FileDocumentManager.getInstance().getFile(document)?.path?.let { path ->
        agent.server.textDocumentDidSave(ProtocolTextDocument.fromPath(path))
      }
    }
  }
}
