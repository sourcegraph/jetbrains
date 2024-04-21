package com.sourcegraph.cody.edit

import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.protocol.ModelUsage
import com.sourcegraph.cody.chat.ui.LlmDropdown
import com.sourcegraph.config.ThemeUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.border.Border
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Pop up a user interface for giving Cody instructions to fix up code at the cursor. */
class EditCommandPrompt(val controller: FixupService, val editor: Editor, dialogTitle: String) :
    Disposable {
  private val logger = Logger.getInstance(EditCommandPrompt::class.java)

  private val offset = editor.caretModel.primaryCaret.offset

  private var dialog: EditCommandPrompt.InstructionsDialog? = null

  private val escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)

  private var connection: MessageBusConnection? = null

  private val escapeAction =
      object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
          dialog?.performCancelAction()
        }
      }

  private var okButton =
      JButton().apply {
        text = "Edit Code"
        foreground = boldLabelColor()
        addActionListener { performOKAction() }

        val enterKeyStroke =
            if (SystemInfo.isMac) {
              // Mac: Command+Enter
              KeyStroke.getKeyStroke(
                  KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx())
            } else {
              // Others: Control+Enter
              KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK)
            }
        registerKeyboardAction(
            { performOKAction() }, enterKeyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW)
      }

  private val instructionsField =
      GhostTextField().apply {
        val fontHeight = getFontMetrics(font).height
        preferredSize =
            Dimension(minOf(getScreenWidth(editor) / 2, DEFAULT_TEXT_FIELD_WIDTH), fontHeight * 4)
        minimumSize = preferredSize
      }

  private val llmDropdown =
      LlmDropdown(
              modelUsage = ModelUsage.EDIT,
              project = controller.project,
              onSetSelectedItem = {},
              this,
              chatModelProviderFromState = null)
          .apply {
            foreground = boldLabelColor()
            background = textFieldBackground()
            addKeyListener(
                object : KeyAdapter() {
                  override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ESCAPE) {
                      dialog?.performCancelAction()
                    }
                  }
                })
          }

  private val historyCursor = HistoryCursor()

  private val dropdownParent =
      FakePanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = true
        background = textFieldBackground()
        add(Box.createHorizontalStrut(10))
        add(llmDropdown)
        add(Box.createHorizontalStrut(10))
        preferredSize = Dimension(instructionsField.width, llmDropdown.preferredSize.height)
      }

  // spacer below the dropdown, making it appear as if it is inside the text field
  private val dropdownSpacer =
      FakePanel().apply {
        isOpaque = true
        background = textFieldBackground()
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        add(Box.createHorizontalGlue())
        minimumSize = Dimension(0, 10)
        preferredSize = minimumSize
      }

  private var titleLabel =
      JLabel(dialogTitle).apply {
        setBorder(BorderFactory.createEmptyBorder(10, LEFT_WIDGET_MARGIN, 10, 10))
        foreground = boldLabelColor()
      }

  private var filePathLabel =
      JLabel().apply {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
        foreground = subduedLabelColor()
      }

  init {
    setupTextField()
    setupKeyListener()
    connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection?.subscribe(UISettingsListener.TOPIC, UISettingsListener { onThemeChange() })
  }

  fun displayPromptUI() {
    ApplicationManager.getApplication().invokeLater {
      val dialog = dialog ?: InstructionsDialog().apply { this@EditCommandPrompt.dialog = this }
      dialog.apply {
        pack()
        shape = makeCornerShape(width, height)
        setLocationRelativeTo(getFrameForEditor(editor) ?: editor.component.rootPane)
        isVisible = true
      }
    }
  }

  private fun getFrameForEditor(editor: Editor): JFrame? {
    return WindowManager.getInstance().getFrame(editor.project ?: return null)
  }

  fun getText(): String = instructionsField.text

  @RequiresEdt
  private fun setupTextField() {
    instructionsField.document.addDocumentListener(
        object : DocumentListener {
          override fun insertUpdate(e: DocumentEvent?) {
            handleDocumentChange()
          }

          override fun removeUpdate(e: DocumentEvent?) {
            handleDocumentChange()
          }

          override fun changedUpdate(e: DocumentEvent?) {
            handleDocumentChange()
          }

          private fun handleDocumentChange() {
            ApplicationManager.getApplication().invokeLater {
              updateOkButtonState()
              checkForInterruptions()
            }
          }
        })
  }

  @RequiresEdt
  private fun updateOkButtonState() {
    okButton.isEnabled = instructionsField.text.isNotBlank()
  }

  @RequiresEdt
  private fun checkForInterruptions() {
    if (editor.isDisposed || editor.isViewer || !editor.document.isWritable) {
      dialog?.performCancelAction()
    }
  }

  @RequiresEdt
  private fun setupKeyListener() {
    instructionsField.addKeyListener(
        object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            when (e.keyCode) {
              KeyEvent.VK_UP -> fetchPreviousHistoryItem()
              KeyEvent.VK_DOWN -> fetchNextHistoryItem()
              KeyEvent.VK_ESCAPE -> {
                dialog?.performCancelAction()
              }
            }
            updateOkButtonState()
          }
        })
  }

  @RequiresEdt
  private fun fetchPreviousHistoryItem() {
    updateTextFromHistory(historyCursor.getPreviousHistoryItem())
  }

  @RequiresEdt
  private fun fetchNextHistoryItem() {
    updateTextFromHistory(historyCursor.getNextHistoryItem())
  }

  @RequiresEdt
  private fun updateTextFromHistory(text: String) {
    instructionsField.text = text
    instructionsField.caretPosition = text.length
    updateOkButtonState()
  }

  // A history navigation helper.
  private inner class HistoryCursor {
    private var historyIndex = -1

    fun getPreviousHistoryItem() = getHistoryItemByDelta(-1)

    fun getNextHistoryItem() = getHistoryItemByDelta(1)

    @RequiresEdt
    private fun getHistoryItemByDelta(delta: Int): String {
      if (promptHistory.isNotEmpty()) {
        historyIndex = (historyIndex + delta).coerceIn(0, promptHistory.size - 1)
        return promptHistory[historyIndex]
      }
      return ""
    }
  }

  private inner class InstructionsDialog : JFrame() {
    init {
      isUndecorated = true
      isAlwaysOnTop = true
      isResizable = true
      defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

      instructionsField.text = controller.getLastPrompt()

      contentPane = generatePromptUI()
      minimumSize = Dimension(DEFAULT_TEXT_FIELD_WIDTH, 0)

      updateOkButtonState()
    }

    override fun getRootPane(): JRootPane {
      val rootPane = super.getRootPane()
      val inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
      val actionMap = rootPane.actionMap

      inputMap.put(escapeKeyStroke, "ESCAPE")
      actionMap.put("ESCAPE", escapeAction)

      return rootPane
    }

    fun performCancelAction() {
      connection?.disconnect()
      connection = null
      dialog = null
      dispose()
    }
  } // InstructionsDialog

  @RequiresEdt
  private fun generatePromptUI(): JPanel {
    return JPanel(BorderLayout()).apply {
      add(createTopRow(), BorderLayout.NORTH)
      add(createBottomRow(), BorderLayout.SOUTH)
      add(createCenterPanel(), BorderLayout.CENTER)
    }
  }

  private fun createTopRow(): JPanel {
    return JPanel(BorderLayout()).apply {
      add(titleLabel, BorderLayout.WEST)

      val (line, col) = editor.offsetToLogicalPosition(offset).let { Pair(it.line, it.column) }
      val file = getFormattedFilePath(FileDocumentManager.getInstance().getFile(editor.document))
      filePathLabel.text = "$file at $line:$col"
      add(filePathLabel, BorderLayout.CENTER)
      object : MouseAdapter() {
            var lastX: Int = 0
            var lastY: Int = 0
            // Debounce to mitigate jitter while dragging.
            var lastUpdateTime = System.currentTimeMillis()

            override fun mousePressed(e: MouseEvent) {
              lastX = e.xOnScreen
              lastY = e.yOnScreen
            }

            override fun mouseDragged(e: MouseEvent) {
              val currentTime = System.currentTimeMillis()
              if (currentTime - lastUpdateTime > 16) { // about 60 fps
                val x: Int = e.xOnScreen
                val y: Int = e.yOnScreen
                SwingUtilities.invokeLater {
                  val loc = UIUtil.getLocationOnScreen(dialog!!.rootPane)!!
                  dialog?.setLocation(loc.x + x - lastX, loc.y + y - lastY)
                  lastX = x
                  lastY = y
                }
                lastUpdateTime = currentTime
              }
            }
          }
          .let {
            addMouseListener(it)
            addMouseMotionListener(it)
          }
    }
  }

  private fun getFormattedFilePath(file: VirtualFile?): String {
    val maxLength = 70
    val fileName = file?.name ?: FILE_PATH_404
    val fullPath = file?.path ?: return fileName
    val project = editor.project ?: return FILE_PATH_404

    val projectRootPath = getProjectRootPath(project, file) ?: return fileName

    val relativePath = fullPath.removePrefix(projectRootPath)
    val truncatedPath =
        if (relativePath.length > maxLength) {
          "…${relativePath.takeLast(maxLength - 1)}"
        } else {
          relativePath
        }

    return truncatedPath.ifEmpty { fileName }
  }

  private fun getProjectRootPath(project: Project, file: VirtualFile?): String? {
    val projectRootManager = ProjectRootManager.getInstance(project)
    val contentRoots = projectRootManager.contentRoots

    // Find the content root that contains the given file
    val contentRoot =
        file?.let { nonNullFile ->
          contentRoots.firstOrNull { VfsUtilCore.isAncestor(it, nonNullFile, false) }
        }

    return contentRoot?.path
  }

  private fun createCenterPanel(): JPanel {
    return FakePanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(instructionsField)
      add(dropdownParent)
      add(dropdownSpacer)
    }
  }

  private fun createBottomRow(): JPanel {
    return JPanel(BorderLayout()).apply {
      add(
          JLabel("[esc] to cancel").apply {
            foreground = subduedLabelColor()
            border = BorderFactory.createEmptyBorder(0, LEFT_WIDGET_MARGIN, 0, 0)
            cursor = Cursor(Cursor.HAND_CURSOR)
            addMouseListener(
                object : MouseAdapter() {
                  override fun mouseClicked(e: MouseEvent) {
                    dialog?.performCancelAction()
                  }
                })
          },
          BorderLayout.WEST)
      add(
          JLabel().apply {
            text =
                if (promptHistory.isNotEmpty()) {
                  "↑↓ for history"
                } else {
                  ""
                }
          },
          BorderLayout.CENTER)
      add(createOKButtonGroup(), BorderLayout.EAST)
    }
  }

  private fun createOKButtonGroup(): JPanel {
    val box =
        JPanel().apply {
          border = JBUI.Borders.empty(5, 10)
          isOpaque = false
          background = textFieldBackground()
          putClientProperty("name", "outerMostChildOfContentPane")
          layout = BoxLayout(this, BoxLayout.X_AXIS)
        }
    box.add(
        JLabel().apply {
          text =
              when {
                SystemInfoRt.isMac -> "⌘↵  "
                SystemInfoRt.isWindows -> "Ctrl+Enter  "
                else -> "^↵  "
              }
        })
    box.add(okButton)
    return box
  }

  @RequiresEdt
  fun performOKAction() {
    val text = instructionsField.text
    if (text.isNotBlank()) {
      addToHistory(text)
      val project = editor.project
      // TODO: How do we show user feedback when an error like this happens?
      if (project == null) {
        logger.warn("Project was null when trying to add an edit session")
        return
      }
      // Kick off the editing command.
      controller.setActiveSession(
          EditSession(controller, editor, project, editor.document, text, llmDropdown.item))
    }
    dialog?.performCancelAction()
  }

  private fun getScreenWidth(editor: Editor): Int {
    val frame = WindowManager.getInstance().getIdeFrame(editor.project)
    val screenSize = frame?.component?.let { SwingUtilities.getWindowAncestor(it).size }
    return screenSize?.width ?: DEFAULT_TEXT_FIELD_WIDTH
  }

  inner class GhostTextField : ExpandableTextField(), FocusListener, Disposable {

    private val unfocusedBorder: Border =
        BorderFactory.createLineBorder(UIManager.getColor("Panel.background"), 1)

    // TODO: Talk to Daniel about how to indicate the text field has the focus.
    @Suppress("unused") private val focusedBorder = unfocusedBorder
    //    private val focusedBorder: Border =
    //        BorderFactory.createLineBorder(UIManager.getColor("Component.focusedBorderColor"), 1)

    init {
      Disposer.register(this@EditCommandPrompt, this@GhostTextField)
      addFocusListener(this)
      border = JBUI.Borders.empty()
    }

    override fun paintComponent(g: Graphics) {
      background = textFieldBackground()
      (g as Graphics2D).background = textFieldBackground()
      super.paintComponent(g)

      if (text.isEmpty()) {
        g.apply {
          setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          color = UIManager.getColor("Component.infoForeground")
          val leftMargin = 30
          drawString(GHOST_TEXT, leftMargin, (fontMetrics.height * 1.5).toInt())
        }
      }
    }

    override fun focusGained(e: FocusEvent?) {
      // border = focusedBorder
      repaint()
    }

    override fun focusLost(e: FocusEvent?) {
      // border = unfocusedBorder
      repaint()
    }

    override fun dispose() {
      removeFocusListener(this)
    }
  } // GhostTextField

  private fun makeCornerShape(width: Int, height: Int): RoundRectangle2D {
    return RoundRectangle2D.Double(
        0.0, 0.0, width.toDouble(), height.toDouble(), CORNER_RADIUS, CORNER_RADIUS)
  }

  override fun dispose() {
    try {
      dialog?.dispose()
    } catch (t: Throwable) {
      logger.warn("Error disposing instructions dialog", t)
    }
  }

  private fun onThemeChange() {
    SwingUtilities.invokeLater {

      // These all have their own backgrounds, so we manage them ourselves.
      dropdownSpacer.background = textFieldBackground()
      dropdownParent.background = textFieldBackground()
      titleLabel.foreground = boldLabelColor()

      dialog?.apply {
        revalidate()
        repaint()
      }
    }
  }

  // A panel that pretends to be part of the TextField, below it, giving it
  // the appearance of having the llm dropdown be "within" the TextField.
  inner class FakePanel : JPanel() {
    init {
      isOpaque = true
      background = textFieldBackground()
    }

    override fun paintComponent(g: Graphics) {
      g.color = background
      (g as Graphics2D).background = background
      super.paintComponent(g)
    }
  }

  companion object {
    // This is a fallback for the rare case when the screen size computations fail.
    const val DEFAULT_TEXT_FIELD_WIDTH: Int = 700

    const val LEFT_WIDGET_MARGIN = 12 // Left margin for top/bottom rows

    // TODO: Put this back when @-includes are in
    // const val GHOST_TEXT = "Instructions (@ to include code)"
    const val GHOST_TEXT = "Type what changes you want to make to this file..."

    private const val CORNER_RADIUS = 16.0

    // Used when the Editor/Document does not have an associated filename.
    private const val FILE_PATH_404 = "unknown file"

    fun subduedLabelColor(): Color =
        UIManager.getColor("Label.disabledForeground").run {
          if (ThemeUtil.isDarkTheme()) this else darker()
        }

    fun boldLabelColor(): Color =
        UIManager.getColor("Label.foreground").run {
          if (ThemeUtil.isDarkTheme()) brighter() else darker()
        }

    fun textFieldBackground(): Color =
        UIManager.getColor("TextField.background").run {
          if (ThemeUtil.isDarkTheme()) darker() else brighter()
        }

    // Going with a global history for now, shared across edit-code prompts.
    val promptHistory = mutableListOf<String>()

    @RequiresEdt
    fun addToHistory(prompt: String) {
      if (prompt.isNotBlank() && !promptHistory.contains(prompt)) {
        promptHistory.add(prompt)
      }
    }
  }
}
