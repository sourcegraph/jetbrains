package com.sourcegraph.cody.chat.actions

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.sourcegraph.cody.chat.ExportChatsBackgroundable
import com.sourcegraph.common.ui.DumbAwareBGTAction
import java.io.File

class ExportChatsAction : DumbAwareBGTAction() {

  private var isRunning = false

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = !isRunning
    e.presentation.description = if (isRunning) "Export in progress..." else "Export Chats As JSON"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    isRunning = true

    // Update default file path to user home if myProject.getBasePath() is not valid
    var outputDir: VirtualFile? = project.baseDir
    if (outputDir == null || !outputDir.exists()) {
      outputDir = VfsUtil.getUserHomeDir()
    }

    val descriptor = FileSaverDescriptor("Cody: Export Chats", "Save as *.$EXTENSION", EXTENSION)

    val saveFileDialog: FileSaverDialog =
        FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

    // Append extension manually to file name on MacOS because FileSaverDialog does not
    // do it automatically.
    val fileName: String = "Untitled" + (if (SystemInfo.isMac) ".$EXTENSION" else "")

    val result = saveFileDialog.save(outputDir, fileName) ?: return

    ExportChatsBackgroundable(
            project,
            onSuccess = { chatHistory ->
              val json = gson.toJson(chatHistory)
              invokeLater {
                WriteAction.run<RuntimeException> {
                  saveTextToFile(json.toByteArray(), result.file)
                }
              }
            },
            onFinished = { isRunning = false })
        .queue()
  }

  @RequiresWriteLock
  private fun saveTextToFile(contentBytes: ByteArray, filePath: File) {
    val localFileSystem = LocalFileSystem.getInstance()
    val parentVirtualFile = localFileSystem.findFileByIoFile(filePath.parentFile) ?: return
    val virtualFile =
        parentVirtualFile.findOrCreateChildData(/* requestor = */ this, /* name = */ filePath.name)

    virtualFile.setBinaryContent(contentBytes)
    VirtualFileManager.getInstance().syncRefresh()
  }

  companion object {
    val gson: Gson = GsonBuilder().create()

    const val EXTENSION = "json"
  }
}
