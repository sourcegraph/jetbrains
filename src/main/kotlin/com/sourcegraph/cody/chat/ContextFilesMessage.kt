package com.sourcegraph.cody.chat

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.AnActionLink
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.sourcegraph.cody.agent.protocol.ContextFile
import com.sourcegraph.cody.agent.protocol.ContextMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.chat.ChatUIConstants.*
import com.sourcegraph.cody.ui.AccordionSection
import java.awt.BorderLayout
import java.awt.Insets
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class ContextFilesMessage(project: Project, contextMessages: List<ContextMessage>) :
    PanelWithGradientBorder(ASSISTANT_MESSAGE_GRADIENT_WIDTH, Speaker.ASSISTANT) {
  init {
    this.layout = BorderLayout()

    val margin = JBInsets.create(Insets(TEXT_MARGIN, TEXT_MARGIN, TEXT_MARGIN, TEXT_MARGIN))
    val contextFileNames =
        contextMessages
            .stream()
            .map<ContextFile>(ContextMessage::file)
            .filter { obj: ContextFile? -> Objects.nonNull(obj) }
            .map(ContextFile::fileName)
            .collect(Collectors.toSet())

    val accordionSection = AccordionSection("Read ${contextFileNames.size} files")
    accordionSection.isOpaque = false
    accordionSection.border = EmptyBorder(margin)
    val fileIndex = AtomicInteger(0)
    contextFileNames.forEach { fileName: String ->
      val filePanel = createFileWithLinkPanel(project, fileName)
      accordionSection.contentPanel.add(filePanel, fileIndex.getAndIncrement())
    }
    add(accordionSection, BorderLayout.CENTER)
  }

  private fun createFileWithLinkPanel(project: Project, fileName: String): JPanel {
    val fileWithoutRedundantPrefix =
        fileName.removePrefix("../../../..").removePrefix(project.baseDir.path)
    val anAction =
        object : DumbAwareAction() {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            val file = project.baseDir.findFileByRelativePath(fileWithoutRedundantPrefix)
            logger.info("Opening a file from the used context (fileName=$fileName file=$file)")
            if (file != null) {
              FileEditorManager.getInstance(project).openFile(file, /*focusEditor=*/ true)
            }
          }
        }
    val goToFile = AnActionLink("@$fileWithoutRedundantPrefix", anAction)
    val panel = JPanel(BorderLayout())
    panel.isOpaque = false
    panel.border = JBUI.Borders.emptyLeft(3)
    panel.add(goToFile, BorderLayout.PAGE_START)
    return panel
  }

  companion object {
    private val logger = Logger.getInstance(ContextFilesMessage::class.java)
  }
}
