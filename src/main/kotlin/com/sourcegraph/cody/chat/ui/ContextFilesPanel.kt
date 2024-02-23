package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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
import com.sourcegraph.common.BrowserOpener.openInBrowser
import java.awt.BorderLayout
import java.awt.Insets
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

  fun updateContentWith(contextFiles: List<ContextFile>?) {
    val contextFileFiles = contextFiles?.mapNotNull { it as? ContextFileFile }

    if (contextFileFiles.isNullOrEmpty()) {
      return
    }

    val margin = JBInsets.create(Insets(TEXT_MARGIN, TEXT_MARGIN, TEXT_MARGIN, TEXT_MARGIN))
    val accordionSection = AccordionSection("Read ${contextFileFiles.size} files")
    accordionSection.isOpaque = false
    accordionSection.border = EmptyBorder(margin)
    contextFileFiles.forEachIndexed { index, contextFile: ContextFileFile ->
      val filePanel = createFileWithLinkPanel(contextFile)
      accordionSection.contentPanel.add(filePanel, index)
    }

    this.removeAll()
    this.isVisible = true
    add(accordionSection, BorderLayout.CENTER)
  }

  @RequiresEdt
  private fun createFileWithLinkPanel(contextFileFile: ContextFileFile): JPanel {
    val anAction =
        object : DumbAwareAction() {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            if (contextFileFile.repoName == null) {
              openInEditor(contextFileFile)
            } else {
              openInBrowser(project, contextFileFile.uri)
            }
          }
        }

    val goToFile = ContextFileActionLink(project, contextFileFile, anAction)
    val panel = JPanel(BorderLayout())
    panel.isOpaque = false
    panel.border = JBUI.Borders.empty(3, 3, 0, 0)
    panel.add(goToFile, BorderLayout.PAGE_START)
    return panel
  }

  private fun openInEditor(contextFileFile: ContextFileFile) {
    val logicalLine = contextFileFile.range?.start?.line ?: 0
    val newUri = contextFileFile.uri.withFragment(null).withQuery(null)
    val contextFilePath = Paths.get(newUri)
    ApplicationManager.getApplication().executeOnPooledThread {
      val findFileByNioFile = LocalFileSystem.getInstance().findFileByNioFile(contextFilePath)
      if (findFileByNioFile != null) {
        ApplicationManager.getApplication().invokeLater {
          OpenFileDescriptor(project, findFileByNioFile, logicalLine, /* logicalColumn= */ 0)
              .navigate(/* requestFocus= */ true)
        }
      }
    }
  }
}
