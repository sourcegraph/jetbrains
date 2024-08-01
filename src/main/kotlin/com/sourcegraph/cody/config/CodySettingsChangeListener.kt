package com.sourcegraph.cody.config

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.config.ConfigUtil

class CodySettingsChangeListener(private val project: Project) : FileDocumentManagerListener {
  override fun fileContentReloaded(currentFile: VirtualFile, document: Document) {
    if (currentFile.toNioPath() == ConfigUtil.getSettingsFile(project)) {
      CodyAgentService.withAgentRestartIfNeeded(project) {
        it.server.extensionConfiguration_didChange(ConfigUtil.getAgentConfiguration(project))
      }
    }
  }
}