package com.sourcegraph.cody

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.DocumentAdapter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.chat.CodyChatMessageHistory
import com.sourcegraph.cody.ui.AutoGrowingTextArea
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.text.DefaultEditorKit

class PromptPanel(
    chatMessageHistory: CodyChatMessageHistory,
    onSendMessageAction: () -> Unit,
    sendButton: JButton,
    isGenerating: () -> Boolean,
) : JLayeredPane() {

  private var isInHistoryMode = true
  private val autoGrowingTextArea = AutoGrowingTextArea(3, 9, this)
  private val scrollPane = autoGrowingTextArea.scrollPane
  val textArea = autoGrowingTextArea.textArea

  init {
    textArea.emptyText.text = "Ask a question about this code..."

    val upperMessageAction: AnAction =
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            if (isInHistoryMode) {
              chatMessageHistory.popUpperMessage(textArea)
            } else {
              val defaultAction = textArea.actionMap[DefaultEditorKit.upAction]
              defaultAction.actionPerformed(null)
            }
          }
        }
    val lowerMessageAction: AnAction =
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            if (isInHistoryMode) {
              chatMessageHistory.popLowerMessage(textArea)
            } else {
              val defaultAction = textArea.actionMap[DefaultEditorKit.downAction]
              defaultAction.actionPerformed(null)
            }
          }
        }
    val sendMessageAction: AnAction =
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            if (sendButton.isEnabled) {
              onSendMessageAction()
              isInHistoryMode = true
            }
          }
        }
    sendMessageAction.registerCustomShortcutSet(DEFAULT_SUBMIT_ACTION_SHORTCUT, textArea)
    upperMessageAction.registerCustomShortcutSet(POP_UPPER_MESSAGE_ACTION_SHORTCUT, textArea)
    lowerMessageAction.registerCustomShortcutSet(POP_LOWER_MESSAGE_ACTION_SHORTCUT, textArea)
    textArea.addKeyListener(
        object : KeyAdapter() {
          override fun keyReleased(e: KeyEvent) {
            val keyCode = e.keyCode
            if (keyCode != KeyEvent.VK_UP && keyCode != KeyEvent.VK_DOWN) {
              isInHistoryMode = textArea.getText().isEmpty()
            }
          }
        })
    textArea.document.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            val empty = textArea.getText().isEmpty()
            sendButton.isEnabled = !empty && !isGenerating()
          }
        })
    val margin = 14
    scrollPane.border = EmptyBorder(JBUI.insets(0, margin, margin, margin))
    scrollPane.background = UIUtil.getPanelBackground()

    // Set initial bounds for the scrollPane (200x200) to ensure proper initialization;
    // later adjusted dynamically based on component resizing in the component listener.
    scrollPane.setBounds(0, 0, 200, 200)

    preferredSize = Dimension(scrollPane.width, scrollPane.height)

    add(scrollPane, DEFAULT_LAYER)

    val jButtonPreferredSize = sendButton.preferredSize
    add(sendButton, PALETTE_LAYER, 0)

    scrollPane.setBounds(0, 0, width, height)

    addComponentListener(
        object : java.awt.event.ComponentAdapter() {
          override fun componentResized(e: java.awt.event.ComponentEvent?) {
            scrollPane.setBounds(0, 0, width, height)
            sendButton.setBounds(
                scrollPane.width - jButtonPreferredSize.width - 10 - margin,
                scrollPane.height - jButtonPreferredSize.height - 10 - margin,
                jButtonPreferredSize.width,
                jButtonPreferredSize.height)
          }
        })
  }

  fun reset() {
    textArea.text = ""
    isInHistoryMode = true
  }

  companion object {
    private val JUST_ENTER = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null)

    val UP = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), null)
    val DOWN = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), null)
    val DEFAULT_SUBMIT_ACTION_SHORTCUT = CustomShortcutSet(JUST_ENTER)
    val POP_UPPER_MESSAGE_ACTION_SHORTCUT = CustomShortcutSet(UP)
    val POP_LOWER_MESSAGE_ACTION_SHORTCUT = CustomShortcutSet(DOWN)
  }
}
