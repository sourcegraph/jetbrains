package com.sourcegraph.find

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.components.JBPanel
import java.awt.FlowLayout
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.KeyStroke

class FooterPanel : JBPanel<FooterPanel>(FlowLayout(FlowLayout.RIGHT)) {
  private val openButton: JButton
  private val openShortcutLabel: JLabel
  private var previewContent: PreviewContent? = null

  init {
    val altEnterShortcut =
        KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.ALT_DOWN_MASK), null)
    val altEnterShortcutText = KeymapUtil.getShortcutText(altEnterShortcut)
    openShortcutLabel = JLabel(altEnterShortcutText)
    openShortcutLabel.isEnabled = false
    openShortcutLabel.isVisible = false
    openButton = JButton("")
    openButton.addActionListener { _ ->
      val previewContent = previewContent ?: return@addActionListener
      try {
        previewContent.openInEditorOrBrowser()
      } catch (e: Exception) {
        val logger = Logger.getInstance(FooterPanel::class.java)
        logger.warn(
            "Error while opening preview content externally: " +
                e.javaClass.getName() +
                ": " +
                e.message)
      }
    }
    add(openShortcutLabel)
    add(openButton)
    setPreviewContent(null)
  }

  fun setPreviewContent(previewContent: PreviewContent?) {
    this.previewContent = previewContent
    openShortcutLabel.isVisible = previewContent != null
    openButton.isEnabled = previewContent != null
    openButton.text =
        if (previewContent == null || previewContent.opensInEditor()) "Open in Editor"
        else "Open in Browser"
  }
}
