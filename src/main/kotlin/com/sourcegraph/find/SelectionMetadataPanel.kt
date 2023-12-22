package com.sourcegraph.find

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.components.labels.LinkLabel
import java.awt.FlowLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

class SelectionMetadataPanel : JPanel(FlowLayout(FlowLayout.LEFT, 0, 8)) {
  private var selectionMetadataLabel: LinkLabel<String>
  private val externalLinkLabel: JLabel
  private val openShortcutLabel: JLabel
  private var previewContent: PreviewContent? = null

  init {
    selectionMetadataLabel =
        LinkLabel("", null) { source, _ ->
          val previewContent = previewContent ?: return@LinkLabel
          try {
            previewContent.openInEditorOrBrowser()
          } catch (e: Exception) {
            val logger = Logger.getInstance(SelectionMetadataPanel::class.java)
            logger.warn("Error opening file in editor: \"" + source.text + "\"", e)
          }
        }
    selectionMetadataLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0))
    externalLinkLabel =
        JLabel("", AllIcons.Ide.External_link_arrow, SwingConstants.LEFT).apply {
          isVisible = false
        }
    val altEnterShortcut =
        KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), null)
    val altEnterShortcutText = KeymapUtil.getShortcutText(altEnterShortcut)
    openShortcutLabel =
        JLabel(altEnterShortcutText).apply {
          setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0))
          setEnabled(false)
          isVisible = false
        }
    add(selectionMetadataLabel)
    add(externalLinkLabel)
    add(openShortcutLabel)
  }

  fun clearSelectionMetadataLabel() {
    previewContent = null
    selectionMetadataLabel.text = ""
    externalLinkLabel.isVisible = false
    openShortcutLabel.isVisible = false
  }

  fun setSelectionMetadataLabel(previewContent: PreviewContent) {
    this.previewContent = previewContent
    selectionMetadataLabel.text = getMetadataText(previewContent)
    externalLinkLabel.isVisible = !previewContent.opensInEditor()
    openShortcutLabel.setToolTipText(
        "Press " +
            openShortcutLabel.text +
            " to open the selected file" +
            if (previewContent.opensInEditor()) " in the editor." else " in your browser.")
    openShortcutLabel.isVisible = true
  }

  private fun getMetadataText(previewContent: PreviewContent): String {
    return when (previewContent.resultType) {
      "file",
      "path" -> previewContent.repoUrl + ":" + previewContent.path
      "repo" -> previewContent.repoUrl
      "symbol" -> previewContent.symbolName + " (" + previewContent.symbolContainerName + ")"
      "diff" -> previewContent.commitMessagePreview ?: ""
      "commit" -> previewContent.commitMessagePreview ?: ""
      else -> ""
    }
  }
}
