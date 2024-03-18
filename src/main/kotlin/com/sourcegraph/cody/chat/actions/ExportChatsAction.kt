package com.sourcegraph.cody.chat.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.sourcegraph.cody.chat.ExportChatsBackgroundable
import com.sourcegraph.common.ui.DumbAwareBGTAction
import java.io.File

class ExportChatsAction : DumbAwareBGTAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    e.presentation.isEnabled = false
    e.presentation.description = "Export in progress..."

    ExportChatsBackgroundable(
            project,
            onSuccess = { json ->
              ApplicationManager.getApplication().invokeLater {
                // Update default file path to user home if myProject.getBasePath() is not valid
                var outputDir: VirtualFile? = project.baseDir
                if (outputDir == null || !outputDir.exists()) {
                  outputDir = VfsUtil.getUserHomeDir()
                }

                val descriptor =
                    FileSaverDescriptor("Cody: Export Chats", "Save as *.$EXTENSION", EXTENSION)

                val saveFileDialog: FileSaverDialog =
                    FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

                // Append extension manually to file name on MacOS because FileSaverDialog does not
                // do it automatically.
                val fileName: String = "Untitled" + (if (SystemInfo.isMac) ".$EXTENSION" else "")
                val result = saveFileDialog.save(outputDir, fileName) ?: return@invokeLater

                saveTextToFile(json.toString(), result.file)
              }
            },
            onFinished = {
              e.presentation.isEnabled = false
              e.presentation.description = "Export chats..."
            })
        .queue()
  }

  private fun saveTextToFile(textContent: String, filePath: File) {
    val contentBytes = textContent.toByteArray()
    val localFileSystem = LocalFileSystem.getInstance()

    val parentVirtualFile = localFileSystem.findFileByIoFile(filePath.parentFile) ?: return
    val virtualFile =
        parentVirtualFile.findOrCreateChildData(/* requestor = */ this, /* name = */ filePath.name)

    WriteAction.run<RuntimeException> {
      virtualFile.setBinaryContent(contentBytes)
      VirtualFileManager.getInstance().syncRefresh()
    }
  }

  companion object {
    const val EXTENSION = "json"
  }
}
