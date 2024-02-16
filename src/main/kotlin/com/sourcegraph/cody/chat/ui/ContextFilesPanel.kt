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
import com.sourcegraph.cody.agent.protocol.Range
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
import kotlin.io.path.absolutePathString

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
  private fun updateFileList(contextFileNames: Map<Path, ContextFile>) {
    val filesAvailableInEditor =
        contextFileNames
            .map { (key, value) ->
              val file = if (value.repoName != null) Paths.get(key.absolutePathString()) else key
              val findFileByNioFile = LocalFileSystem.getInstance().findFileByNioFile(file)
              findFileByNioFile ?: return@map null
              Pair(findFileByNioFile, value as? ContextFileFile)
            }
            .filterNotNull()
            .toList()

    ApplicationManager.getApplication().invokeLater {
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
    val (startLine, endLine) = contextFileFile?.range?.intellijRange() ?: Pair(null, null)

    return buildString {
      append("@$projectRelativeFilePath")
      if (startLine != null && endLine != null) {
        if (startLine != endLine) {
          append(":$startLine-$endLine")
        } else {
          append(":$startLine")
        }
      }
    }
  }

  fun updateContentWith(contextFiles: List<ContextFile>?) {
    if (contextFiles.isNullOrEmpty()) {
      return
    }
    this.removeAll()

    val contextFileNames = mutableMapOf<Path, ContextFile>()
    for (contextFile in contextFiles) {
      val contextFileName =
          if (contextFile.repoName != null) {
            Paths.get("${project.basePath}/${contextFile.uri.path}")
          } else {
            val newUri = contextFile.uri.withFragment(null).withQuery(null)
            Paths.get(newUri)
          }

      val existingValue = contextFileNames[contextFileName] as? ContextFileFile
      val newValue = contextFile as? ContextFileFile
      val isNewValueNarrower = newValue?.range?.isNarrowerThan(existingValue?.range) ?: false

      if (existingValue == null || isNewValueNarrower) {
        contextFileNames[contextFileName] = contextFile
      }
    }

    ApplicationManager.getApplication().executeOnPooledThread { updateFileList(contextFileNames) }
  }
}

fun Range.isNarrowerThan(otherRange: Range?): Boolean {
  if (otherRange == null) {
    return false
  }

  val thisRangeWidth = this.end.line - this.start.line
  val otherRangeWidth = otherRange.end.line - otherRange.start.line
  return thisRangeWidth < otherRangeWidth
}
