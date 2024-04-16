package com.sourcegraph.cody.listeners

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService.Companion.withAgent
import com.sourcegraph.cody.agent.protocol.ProtocolTextDocument.Companion.fromVirtualFile

class CodyFileEditorListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    withAgent(source.project) { agent: CodyAgent -> fileOpened(source, agent, file) }
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    val protocolTextFile = fromVirtualFile(source, file)
    withAgent(source.project) { agent: CodyAgent ->
      agent.server.textDocumentDidClose(protocolTextFile)
    }
  }

  companion object {
    fun fileOpened(source: FileEditorManager, codyAgent: CodyAgent, file: VirtualFile) {
      val textDocument = fromVirtualFile(source, file)
      codyAgent.server.textDocumentDidOpen(textDocument)
    }
  }
}
