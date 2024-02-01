package com.sourcegraph.cody

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.DocumentAdapter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.ContextFile
import com.sourcegraph.cody.chat.ChatSession
import com.sourcegraph.cody.chat.CodyChatMessageHistory
import com.sourcegraph.cody.chat.ui.SendButton
import com.sourcegraph.cody.ui.AutoGrowingTextArea
import com.sourcegraph.cody.vscode.CancellationToken
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.DefaultListModel
import javax.swing.JLayeredPane
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.border.EmptyBorder
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import javax.swing.event.DocumentEvent

class PromptPanel(
    private val chatSession: ChatSession,
) : JLayeredPane() {

  /** View components */
  private val autoGrowingTextArea = AutoGrowingTextArea(5, 9, this)
  private val scrollPane = autoGrowingTextArea.scrollPane
  private val textArea = autoGrowingTextArea.textArea
  private val sendButton = SendButton()
  private var contextFilesSelectorModel = DefaultListModel<DisplayedContextFile>()
  private val contextFilesSelector = JList(contextFilesSelectorModel)
  private val contextFilesScroller = JScrollPane(contextFilesSelector)

  /** Externally updated state */
  private val selectedContextFiles: ArrayList<ContextFile> = ArrayList()

  /** Related components */
  private val promptMessageHistory =
      CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY, chatSession)

  init {
    /** Initialize view */
    textArea.emptyText.text = "Ask a question about this code..."
    scrollPane.border = EmptyBorder(JBUI.emptyInsets())
    scrollPane.background = UIUtil.getPanelBackground()

    // Set initial bounds for the scrollPane (100x100) to ensure proper initialization;
    // later adjusted dynamically based on component resizing in the component listener.
    scrollPane.setBounds(0, 0, 100, 100)
    add(scrollPane, DEFAULT_LAYER)
    scrollPane.setBounds(0, 0, width, scrollPane.preferredSize.height)

    contextFilesSelector.border = EmptyBorder(JBUI.emptyInsets())
    add(contextFilesScroller, PALETTE_LAYER, 0)

    add(sendButton, PALETTE_LAYER, 0)

    preferredSize = Dimension(scrollPane.width, scrollPane.height)

    /** Add listeners */
    addAncestorListener(
        object : AncestorListener {
          override fun ancestorAdded(event: AncestorEvent?) {
            textArea.requestFocusInWindow()
            textArea.caretPosition = textArea.document.length
          }

          override fun ancestorRemoved(event: AncestorEvent?) {}

          override fun ancestorMoved(event: AncestorEvent?) {}
        })
    addComponentListener(
        object : ComponentAdapter() {
          override fun componentResized(e: ComponentEvent?) {
            // HACK
            val jButtonPreferredSize = sendButton.preferredSize
            sendButton.setBounds(
                scrollPane.width - jButtonPreferredSize.width,
                scrollPane.height - jButtonPreferredSize.height,
                jButtonPreferredSize.width,
                jButtonPreferredSize.height)
            refreshViewLayout()
          }
        })

    // Add user action listeners
    sendButton.addActionListener { _ -> didSubmitChatMessage() }
    textArea.document.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            refreshSendButton()
            didUserInputChange(textArea.text)
          }
        })
    contextFilesSelector.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            contextFilesSelector.selectedIndex = contextFilesSelector.locationToIndex(e.getPoint())
            didSelectContextFile()
            textArea.requestFocusInWindow()
          }
        })
    for (shortcut in listOf(ENTER, UP, DOWN, TAB)) { // key listeners
      object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
              didUseShortcut(shortcut)
            }
          }
          .registerCustomShortcutSet(shortcut, textArea)
    }
  }

  private fun didUseShortcut(shortcut: CustomShortcutSet) {
    if (contextFilesSelector.model.size > 0) {
      when (shortcut) {
        UP -> setSelectedContextFileIndex(-1)
        DOWN -> setSelectedContextFileIndex(1)
        ENTER,
        TAB -> didSelectContextFile()
      }
      return
    }
    when (shortcut) {
      ENTER -> if (sendButton.isEnabled) didSubmitChatMessage()
      UP -> promptMessageHistory.popUpperMessage(textArea)
      DOWN -> promptMessageHistory.popLowerMessage(textArea)
    }
  }

  /** View handlers */
  private fun didSubmitChatMessage() {
    val cf = findContextFiles(selectedContextFiles, textArea.text)
    val text = textArea.text

    // Reset text
    promptMessageHistory.messageSent(text)
    textArea.text = ""
    selectedContextFiles.clear()

    chatSession.sendMessage(text, cf)
  }

  private fun didSelectContextFile() {
    if (contextFilesSelector.selectedIndex == -1) return

    val selected = contextFilesSelector.model.getElementAt(contextFilesSelector.selectedIndex)
    this.selectedContextFiles.add(selected.contextFile)
    val cfDisplayPath = selected.toString()
    val expr = findAtExpressions(textArea.text).lastOrNull() ?: return

    textArea.replaceRange("@${cfDisplayPath} ", expr.startIndex, expr.endIndex)

    setContextFilesSelector(listOf())
    refreshViewLayout()
  }

  private fun didUserInputChange(text: String) {
    val exp = findAtExpressions(text).lastOrNull()
    if (exp == null ||
        exp.endIndex <
            text.length) { // TODO(beyang): instead of text.length, should be current cursor index
      setContextFilesSelector(listOf())
      refreshViewLayout()
      return
    }
    this.chatSession.sendWebviewMessage(
        WebviewMessage(command = "getUserContext", submitType = "user", query = exp.value))
  }

  /** State updaters */
  private fun setSelectedContextFileIndex(increment: Int) {
    var newSelectedIndex =
        (contextFilesSelector.selectedIndex + increment) % contextFilesSelector.model.size
    if (newSelectedIndex < 0) {
      newSelectedIndex += contextFilesSelector.model.size
    }
    contextFilesSelector.selectedIndex = newSelectedIndex
    refreshViewLayout()
  }

  /** View updaters */
  @RequiresEdt
  private fun refreshViewLayout() {
    // get the height of the context files list based on font height and number of context files
    val contextFilesHeight = contextFilesSelector.preferredSize.height
    contextFilesScroller.size = Dimension(scrollPane.width, contextFilesHeight)

    val margin = 10
    scrollPane.setBounds(0, contextFilesHeight, width, scrollPane.preferredSize.height + margin)
    preferredSize = Dimension(scrollPane.width, scrollPane.height + contextFilesHeight)

    sendButton.setLocation(
        scrollPane.width - sendButton.preferredSize.width,
        scrollPane.height + contextFilesSelector.height - sendButton.preferredSize.height)

    revalidate()
  }

  @RequiresEdt
  private fun refreshSendButton() {
    sendButton.isEnabled =
        textArea.getText().isNotEmpty() && chatSession.getCancellationToken().isDone
  }

  /** External prop setters */
  fun registerCancellationToken(cancellationToken: CancellationToken) {
    cancellationToken.onFinished {
      ApplicationManager.getApplication().invokeLater { refreshSendButton() }
    }
  }

  @RequiresEdt
  fun setContextFilesSelector(newUserContextFiles: List<ContextFile>) {
    val changed = contextFilesSelectorModel.elements().toList() != newUserContextFiles
    if (changed) {
      val newModel = DefaultListModel<DisplayedContextFile>()
      newModel.addAll(newUserContextFiles.map { f -> DisplayedContextFile(f) })
      contextFilesSelector.model = newModel
      contextFilesSelectorModel = newModel

      if (newUserContextFiles.isNotEmpty()) {
        contextFilesSelector.selectedIndex = 0
      } else {
        contextFilesSelector.selectedIndex = -1
      }
      refreshViewLayout()
    }
  }

  companion object {
    private const val CHAT_MESSAGE_HISTORY_CAPACITY = 100
    private val KEY_ENTER = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null)
    private val KEY_UP = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), null)
    private val KEY_DOWN = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), null)
    private val KEY_TAB = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), null)

    val ENTER = CustomShortcutSet(KEY_ENTER)
    val UP = CustomShortcutSet(KEY_UP)
    val DOWN = CustomShortcutSet(KEY_DOWN)
    val TAB = CustomShortcutSet(KEY_TAB)
  }
}

data class DisplayedContextFile(val contextFile: ContextFile) {
  override fun toString(): String {
    return displayPath(contextFile)
  }
}

data class AtExpression(
    val startIndex: Int,
    val endIndex: Int,
    val rawValue: String,
    val value: String
)

val atExpressionPattern = """(@(?:\\\s|[^\s])+)(?:\s|$)""".toRegex()

fun findAtExpressions(text: String): List<AtExpression> {
  val matches = atExpressionPattern.findAll(text)
  val expressions = ArrayList<AtExpression>()
  for (match in matches) {
    val subMatch = match.groups.get(1)
    if (subMatch != null) {
      val value = subMatch.value.substring(1).replace("\\ ", " ")
      expressions.add(
          AtExpression(subMatch.range.first, subMatch.range.last + 1, subMatch.value, value))
    }
  }
  return expressions
}

fun findContextFiles(contextFiles: List<ContextFile>, text: String): List<ContextFile> {
  val atExpressions = findAtExpressions(text)
  return contextFiles.filter { f -> atExpressions.any { it.value == displayPath(f) } }
}

// TODO(beyang): temporary displayPath implementation, should be updated to mirror what the VS Code
// plugin does
fun displayPath(contextFile: ContextFile): String {
  // if the path contains more than three components, display the last three
  val path = contextFile.uri.path

  // split path on separator (OS agnostic)
  val pathComponents = path.split(File.separator)
  if (pathComponents.size > 3) {
    return "...${File.separator}${pathComponents.subList(pathComponents.size - 3, pathComponents.size).joinToString(File.separator)}"
  }
  return path
}
