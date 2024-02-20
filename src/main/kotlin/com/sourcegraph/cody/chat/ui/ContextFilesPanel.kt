package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.AnActionLink
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.withFragment
import com.intellij.util.withQuery
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.ContextFile
import com.sourcegraph.cody.agent.protocol.ContextFileFile
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ChatUIConstants.ASSISTANT_MESSAGE_GRADIENT_WIDTH
import com.sourcegraph.cody.chat.ChatUIConstants.TEXT_MARGIN
import com.sourcegraph.cody.ui.AccordionSection
import java.awt.BorderLayout
import java.awt.Insets
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class ContextFilesPanel(
    val project: Project,
    chatMessage: ChatMessage,
) : PanelWithGradientBorder(ASSISTANT_MESSAGE_GRADIENT_WIDTH, Speaker.ASSISTANT) {
  init {
    this.layout = BorderLayout()
    isVisible = false

    updateContentWith(chatMessage.contextFiles)
  }

  @RequiresBackgroundThread
  private fun updateFileList(pathsWithContextFiles: List<Pair<Path, ContextFile>>) {
    val filesAvailableInEditor =
        pathsWithContextFiles
            .map { (path, contextFile) ->
              val findFileByNioFile = LocalFileSystem.getInstance().findFileByNioFile(path)
              findFileByNioFile ?: return@map null
              Pair(findFileByNioFile, contextFile as? ContextFileFile)
            }
            .filterNotNull()
            .toList()

    ApplicationManager.getApplication().invokeLater {
      this.removeAll()
      this.isVisible = filesAvailableInEditor.isNotEmpty()

      val margin = JBInsets.create(Insets(TEXT_MARGIN, TEXT_MARGIN, TEXT_MARGIN, TEXT_MARGIN))
      val accordionSection = AccordionSection("Read ${filesAvailableInEditor.size} files")
      accordionSection.isOpaque = false
      accordionSection.border = EmptyBorder(margin)
      filesAvailableInEditor.forEachIndexed { index, file: Pair<VirtualFile, ContextFileFile?> ->
        val filePanel = createFileWithLinkPanel(file.first, file.second)
        accordionSection.contentPanel.add(filePanel, index)
      }
      add(accordionSection, BorderLayout.CENTER)
    }
  }

  @RequiresEdt
  private fun createFileWithLinkPanel(
      file: VirtualFile,
      contextFileFile: ContextFileFile?
  ): JPanel {
    val projectRelativeFilePath = file.path.removePrefix(project.basePath ?: "")
    val anAction =
        object : DumbAwareAction() {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            val logicalLine = contextFileFile?.range?.start?.line ?: 0
            OpenFileDescriptor(project, file, logicalLine, /* logicalColumn= */ 0)
                .navigate(/* requestFocus= */ true)
          }
        }
    val actionText = getActionTextForFileLinkAction(contextFileFile, projectRelativeFilePath)
    val goToFile = AnActionLink(actionText, anAction)
    goToFile.toolTipText = actionText
    val panel = JPanel(BorderLayout())
    panel.isOpaque = false
    panel.border = JBUI.Borders.emptyLeft(3)
    panel.add(goToFile, BorderLayout.PAGE_START)
    return panel
  }

  private fun getActionTextForFileLinkAction(
      contextFileFile: ContextFileFile?,
      projectRelativeFilePath: String
  ): String {
    val intelliJRange = contextFileFile?.range?.intellijRange()

    return buildString {
      append("@$projectRelativeFilePath")
      if (intelliJRange != null) {
        if (intelliJRange.first != intelliJRange.second) {
          append(":${intelliJRange.first}-${intelliJRange.second}")
        } else {
          append(":${intelliJRange.first}")
        }
      }
    }
  }

  fun updateContentWith(contextFiles: List<ContextFile>?) {
    if (contextFiles.isNullOrEmpty()) {
      return
    }

    val pathsWithContextFiles =
        contextFiles
            .map { contextFile ->
              val contextFilePath =
                  if (contextFile.repoName != null) {
                    val path = contextFile.uri.path.split("blob/").lastOrNull()
                    path.let {
                      Paths.get("${project.basePath}/$it") // todo: reference the remote file
                    }
                  } else {
                    val newUri = contextFile.uri.withFragment(null).withQuery(null)
                    Paths.get(newUri)
                  }

              contextFilePath to contextFile
            }
            .filter { (a, _) -> a != null }

    ApplicationManager.getApplication().executeOnPooledThread {
      updateFileList(pathsWithContextFiles)
    }
  }
}
