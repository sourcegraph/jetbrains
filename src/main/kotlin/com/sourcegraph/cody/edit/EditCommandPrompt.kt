package com.sourcegraph.cody.edit

import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
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
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
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
          clearActivePrompt()
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
        text = lastPrompt
        if (text.isBlank() && promptHistory.isNotEmpty()) {
          text = promptHistory.getPrevious()
        }
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
                      clearActivePrompt()
                    }
                  }
                })
          }

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
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10))
        foreground = boldLabelColor()
      }

  private var filePathLabel =
      JLabel().apply {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
        foreground = subduedLabelColor()
      }

  private val documentListener =
      object : BulkAwareDocumentListener {
        override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
          clearActivePrompt()
        }
      }

  private val editorFactoryListener =
      object : EditorFactoryListener {
        override fun editorReleased(event: EditorFactoryEvent) {
          if (editor != event.editor) return
          // Tab was closed.
          clearActivePrompt()
        }
      }

  private val tabFocusListener =
      object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          val oldEditor = event.oldEditor ?: return
          if (oldEditor != editor) return
          // Our tab lost the focus.
          clearActivePrompt()
        }
      }

  private val ideFocusMonitor = FocusMonitor()

  init {
    connection = ApplicationManager.getApplication().messageBus.connect(this)
    registerListeners()
    // Don't reset the session, just any previous instructions dialog.
    controller.currentEditPrompt.get()?.clearActivePrompt()

    setupTextField()
    setupKeyListener()
    connection!!.subscribe(UISettingsListener.TOPIC, UISettingsListener { onThemeChange() })

    ApplicationManager.getApplication().invokeLater {
      val dialog = dialog ?: InstructionsDialog().apply { this@EditCommandPrompt.dialog = this }
      dialog.apply {
        pack()
        shape = makeCornerShape(width, height)
        updateDialogPosition()
        isVisible = true
      }
    }
  }

  private fun updateDialogPosition() {
    // Convert caret position to screen coordinates.
    val pointInEditor = editor.visualPositionToXY(editor.caretModel.visualPosition)

    if (editor.scrollingModel.visibleArea.contains(pointInEditor)) { // caret is visible
      val locationOnScreen = editor.contentComponent.locationOnScreen

      // Calculate the absolute screen position for the dialog, just below current line.
      val dialogX = locationOnScreen.x + 100 // Position it consistently for now.
      val dialogY = locationOnScreen.y + pointInEditor.y + editor.lineHeight
      dialog?.location = Point(dialogX, dialogY)
    } else {
      dialog?.setLocationRelativeTo(getFrameForEditor(editor) ?: editor.component.rootPane)
    }
  }

  private fun registerListeners() {
    // Close dialog on document changes (user edits).
    editor.document.addDocumentListener(documentListener)
    // Close dialog when user switches to a different ab.
    connection?.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, tabFocusListener)
    // Close dialog when user closes the document. This call makes the listener auto-release.
    EditorFactoryImpl.getInstance().addEditorFactoryListener(editorFactoryListener, this)
  }

  private fun unregisterListeners() {
    try {
      editor.document.removeDocumentListener(documentListener)
      // tab focus listener will unregister when we disconnect from the message bus.
    } catch (x: Exception) {
      logger.warn("Error removing listeners", x)
    }
  }

  private fun clearActivePrompt() {
    dialog?.performCancelAction()
  }

  private fun getFrameForEditor(editor: Editor): JFrame? {
    return WindowManager.getInstance().getFrame(editor.project ?: return null)
  }

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
      clearActivePrompt()
    }
  }

  @RequiresEdt
  private fun setupKeyListener() {
    instructionsField.addKeyListener(
        object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            when (e.keyCode) {
              KeyEvent.VK_UP -> instructionsField.setTextAndSelectAll(promptHistory.getPrevious())
              KeyEvent.VK_DOWN -> instructionsField.setTextAndSelectAll(promptHistory.getNext())
              KeyEvent.VK_ESCAPE -> {
                clearActivePrompt()
              }
            }
            updateOkButtonState()
          }
        })
  }

  private inner class InstructionsDialog : JFrame() {
    init {
      isUndecorated = true
      isAlwaysOnTop = true
      isResizable = true
      defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

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
      try {
        instructionsField.text?.let { lastPrompt = it }
        connection?.disconnect()
        connection = null
        dialog = null
        dispose()
      } catch (x: Exception) {
        logger.warn("Error cancelling edit command prompt", x)
      }
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
    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      border = BorderFactory.createEmptyBorder(0, 20, 0, 12)
      add(
          JLabel("[esc] to cancel").apply {
            foreground = subduedLabelColor()
            cursor = Cursor(Cursor.HAND_CURSOR)
            addMouseListener(
                object : MouseAdapter() {
                  override fun mouseClicked(e: MouseEvent) {
                    clearActivePrompt()
                  }
                })
          })

      add(Box.createHorizontalGlue())

      add(
          JLabel().apply {
            text = if (promptHistory.isNotEmpty()) "↑↓ for history" else ""
            horizontalAlignment = JLabel.CENTER
          })

      add(Box.createHorizontalGlue())
      add(createOKButtonGroup())
    }
  }

  private fun createOKButtonGroup(): JPanel {
    return JPanel().apply {
      border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
      isOpaque = false
      background = textFieldBackground()
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      add(
          JLabel().apply {
            text =
                when {
                  SystemInfoRt.isMac -> "⌘↵  "
                  SystemInfoRt.isWindows -> "Ctrl+Enter  "
                  else -> "^↵  "
                }
          })
      add(okButton)
    }
  }

  @RequiresEdt
  fun performOKAction() {
    val text = instructionsField.text
    if (text.isNotBlank()) {
      promptHistory.add(text)
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
    clearActivePrompt()
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

    // This is used by the up/down arrow keys to insert a history item.
    fun setTextAndSelectAll(newContents: String?) {
      if (newContents != null) {
        text = newContents
        selectAll()
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
    unregisterListeners()
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

  // Closes the dialog when the project window loses the focus.
  // This is because it has a tendency to remain at the front of other apps.
  private inner class FocusMonitor : Disposable {
    private var creationTime = System.currentTimeMillis()
    private val window: Window? = getFrameForEditor(editor)

    private val focusListener =
        object : FocusListener {
          override fun focusGained(e: FocusEvent?) {}

          override fun focusLost(e: FocusEvent?) {
            if (isReady()) {
              clearActivePrompt()
            }
          }
        }

    private val windowFocusListener =
        object : WindowFocusListener {
          override fun windowGainedFocus(e: WindowEvent?) {}

          override fun windowLostFocus(e: WindowEvent?) {
            if (isReady()) {
              clearActivePrompt()
            }
          }
        }

    init {
      Disposer.register(this@EditCommandPrompt, this)
      window?.addWindowFocusListener(windowFocusListener)
      window?.addFocusListener(focusListener)
    }

    // Slight debounce to keep it from closing as it's trying to open.
    fun isReady() = System.currentTimeMillis() - creationTime > 1000

    override fun dispose() {
      window?.removeWindowFocusListener(windowFocusListener)
      window?.removeFocusListener(focusListener)
    }
  }

  companion object {
    // This is a fallback for the rare case when the screen size computations fail.
    const val DEFAULT_TEXT_FIELD_WIDTH: Int = 700

    // TODO: Put this back when @-includes are in
    // const val GHOST_TEXT = "Instructions (@ to include code)"
    const val GHOST_TEXT = "Type what changes you want to make to this file..."

    private const val CORNER_RADIUS = 16.0

    // Used when the Editor/Document does not have an associated filename.
    private const val FILE_PATH_404 = "unknown file"

    // I don't cache these because it caused problems with theme switches.
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

    // The last text the user typed in without saving it, for continuity.
    var lastPrompt: String = ""

    private const val HISTORY_CAPACITY = 100
    val promptHistory = HistoryManager<String>(HISTORY_CAPACITY)
  }
}
