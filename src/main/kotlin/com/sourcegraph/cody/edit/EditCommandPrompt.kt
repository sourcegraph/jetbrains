package com.sourcegraph.cody.edit

import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
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
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.protocol.ModelUsage
import com.sourcegraph.cody.chat.ui.LlmDropdown
import com.sourcegraph.cody.edit.sessions.EditCodeSession
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
import java.awt.event.ActionEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.InputEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
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
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Pop up a user interface for giving Cody instructions to fix up code at the cursor. */
class EditCommandPrompt(val controller: FixupService, val editor: Editor, dialogTitle: String) :
    JFrame(), Disposable {
  private val logger = Logger.getInstance(EditCommandPrompt::class.java)

  private val offset = editor.caretModel.primaryCaret.offset

  private val escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)

  private var connection: MessageBusConnection? = null

  private val isDisposed: AtomicBoolean = AtomicBoolean(false)

  private val escapeAction =
      object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
          clearActivePrompt()
        }
      }

  // Key for activating the OK button. It's not a globally registered action.
  // We use a local action and just wire it up manually.
  private val enterKeyStroke =
      if (SystemInfo.isMac) {
        // Mac: Command+Enter
        KeyStroke.getKeyStroke(
            KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx())
      } else {
        // Others: Control+Enter
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK)
      }

  private var okButton =
      JButton().apply {
        text = "Edit Code"
        foreground = boldLabelColor()

        addActionListener { performOKAction() }
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
      TextAreaPanel().apply {
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
      TextAreaPanel().apply {
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
      object : JLabel() {
            override fun processMouseEvent(e: MouseEvent) {
              parent.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, parent))
            }

            override fun processMouseMotionEvent(e: MouseEvent) {
              parent.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, parent))
            }
          }
          .apply {
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

  private val focusListener =
      object : FocusListener {
        override fun focusGained(e: FocusEvent?) {}

        override fun focusLost(e: FocusEvent?) {
          clearActivePrompt()
        }
      }

  private val windowFocusListener =
      object : WindowFocusListener {
        override fun windowGainedFocus(e: WindowEvent?) {}

        override fun windowLostFocus(e: WindowEvent?) {
          clearActivePrompt()
        }
      }

  private var resizeDirection: ResizeDirection? = null
  private var lastMouseX = 0
  private var lastMouseY = 0

  // Note: Must be created on EDT, although we can't annotate it as such.
  init {
    connection = ApplicationManager.getApplication().messageBus.connect(this)
    registerListeners()
    // Don't reset the session, just any previous instructions dialog.
    controller.currentEditPrompt.get()?.clearActivePrompt()

    setupTextField()
    setupKeyListener()
    connection!!.subscribe(UISettingsListener.TOPIC, UISettingsListener { onThemeChange() })
    addFrameDragListeners()

    isUndecorated = true
    isAlwaysOnTop = true
    isResizable = true
    defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
    minimumSize = Dimension(DEFAULT_TEXT_FIELD_WIDTH, 0)

    contentPane = DoubleBufferedRootPane()
    generatePromptUI()
    updateOkButtonState()
    pack()

    shape = makeCornerShape(width, height)
    updateDialogPosition()
    isVisible = true
  }

  private fun updateDialogPosition() {
    // Convert caret position to screen coordinates.
    val pointInEditor = editor.visualPositionToXY(editor.caretModel.visualPosition)

    if (editor.scrollingModel.visibleArea.contains(pointInEditor)) { // caret is visible
      val locationOnScreen = editor.contentComponent.locationOnScreen

      // Calculate the absolute screen position for the dialog, just below current line.
      val dialogX = locationOnScreen.x + 100 // Position it consistently for now.
      val dialogY = locationOnScreen.y + pointInEditor.y + editor.lineHeight
      location = Point(dialogX, dialogY)
    } else {
      setLocationRelativeTo(getFrameForEditor(editor) ?: editor.component.rootPane)
    }
  }

  private fun registerListeners() {
    // Close dialog on document changes (user edits).
    editor.document.addDocumentListener(documentListener)

    // Close dialog when user switches to a different ab.
    connection?.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, tabFocusListener)

    // Close dialog when user closes the document. This call makes the listener auto-release.
    EditorFactoryImpl.getInstance().addEditorFactoryListener(editorFactoryListener, this)

    // Close dialog if window loses focus.
    addWindowFocusListener(windowFocusListener)
    addFocusListener(focusListener)
  }

  private fun addFrameDragListeners() {
    addMouseListener(
        object : MouseAdapter() {
          override fun mousePressed(e: MouseEvent) {
            resizeDirection = getResizeDirection(e.point)
            lastMouseX = e.xOnScreen
            lastMouseY = e.yOnScreen
            updateCursor()
          }

          override fun mouseReleased(e: MouseEvent) {
            resizeDirection = null
            cursor = Cursor.getDefaultCursor()
          }

          override fun mouseEntered(e: MouseEvent) {
            updateCursor()
          }

          override fun mouseExited(e: MouseEvent) {
            cursor = Cursor.getDefaultCursor()
          }

          override fun mouseMoved(e: MouseEvent) {
            updateCursor()
          }
        })

    addMouseMotionListener(
        object : MouseMotionAdapter() {
          override fun mouseDragged(e: MouseEvent) {
            val direction = resizeDirection
            if (direction != null) {
              val newX = e.xOnScreen
              val newY = e.yOnScreen
              val deltaX = newX - lastMouseX
              val deltaY = newY - lastMouseY

              val newWidth = width + (if (direction.isHorizontal) deltaX else 0)
              val newHeight = height + (if (direction.isVertical) deltaY else 0)

              setSize(newWidth, newHeight)

              lastMouseX = newX
              lastMouseY = newY
            }
          }
        })
  }

  private fun updateCursor() {
    val direction = resizeDirection
    cursor =
        when (direction) {
          ResizeDirection.NORTH_WEST -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)
          ResizeDirection.NORTH -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
          ResizeDirection.NORTH_EAST -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)
          ResizeDirection.WEST -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
          ResizeDirection.EAST -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)
          ResizeDirection.SOUTH_WEST -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR)
          ResizeDirection.SOUTH -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
          ResizeDirection.SOUTH_EAST -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)
          else -> Cursor.getDefaultCursor()
        }
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    super.setBounds(x, y, width, height)
    shape = makeCornerShape(width, height)
  }

  private fun unregisterListeners() {
    try {
      editor.document.removeDocumentListener(documentListener)
      removeWindowFocusListener(windowFocusListener)
      removeFocusListener(focusListener)
      // tab focus listener will unregister when we disconnect from the message bus.
    } catch (x: Exception) {
      logger.warn("Error removing listeners", x)
    }
  }

  private fun clearActivePrompt() {
    performCancelAction()
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

  override fun getRootPane(): JRootPane {
    val rootPane = super.getRootPane()
    val inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
    val actionMap = rootPane.actionMap

    inputMap.put(escapeKeyStroke, "ESCAPE")
    actionMap.put("ESCAPE", escapeAction)

    return rootPane
  }

  private fun performCancelAction() {
    try {
      isVisible = false
      instructionsField.text?.let { lastPrompt = it } // Save last thing they typed.
      connection?.disconnect()
      connection = null
      dispose()
    } catch (x: Exception) {
      logger.warn("Error cancelling edit command prompt", x)
    }
  }

  @RequiresEdt
  private fun generatePromptUI() {
    contentPane.layout = BorderLayout()
    contentPane.apply {
      add(createTopRow(), BorderLayout.NORTH)
      add(createCenterPanel(), BorderLayout.CENTER)
      add(createBottomRow(), BorderLayout.SOUTH)
    }
  }

  private fun createTopRow(): JPanel {
    return JPanel(BorderLayout()).apply {
      add(titleLabel, BorderLayout.WEST)
      val (line, col) = editor.offsetToLogicalPosition(offset).let { Pair(it.line, it.column) }
      val virtualFile = FileDocumentManager.getInstance().getFile(editor.document)
      val file = getFormattedFilePath(virtualFile)
      filePathLabel.text = "$file at $line:$col"
      filePathLabel.toolTipText = virtualFile?.path
      add(filePathLabel, BorderLayout.CENTER)
      // Listen for mouse-drag in the title bar, and move the window.
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
                  val loc = UIUtil.getLocationOnScreen(rootPane)!!
                  this@EditCommandPrompt.setLocation(loc.x + x - lastX, loc.y + y - lastY)
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
    return TextAreaPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(
          JScrollPane(instructionsField).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
          })
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
      border = BorderFactory.createEmptyBorder(4, 0, 4, 4)
      isOpaque = false
      background = textFieldBackground()
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      add(
          JLabel().apply {
            text = KeymapUtil.getShortcutText(KeyboardShortcut(enterKeyStroke, null))
            // Spacing between key shortcut and button.
            border = BorderFactory.createEmptyBorder(0, 0, 0, 12)
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
      controller.setActiveSession(EditCodeSession(controller, editor, text, llmDropdown.item))
    }
    clearActivePrompt()
  }

  private fun getScreenWidth(editor: Editor): Int {
    val frame = WindowManager.getInstance().getIdeFrame(editor.project)
    val screenSize = frame?.component?.let { SwingUtilities.getWindowAncestor(it).size }
    return screenSize?.width ?: DEFAULT_TEXT_FIELD_WIDTH
  }

  // TODO: Refactor this into a standalone class.
  private inner class GhostTextField : JTextArea(5, 20), FocusListener, Disposable {

    private inner class GhostTextDocumentListener : DocumentListener {
      private var previousTextEmpty = true

      override fun insertUpdate(e: DocumentEvent) {
        handleDocumentChange(e)
      }

      override fun removeUpdate(e: DocumentEvent) {
        handleDocumentChange(e)
      }

      override fun changedUpdate(e: DocumentEvent) {
        // Ignore changedUpdate events
      }

      private fun handleDocumentChange(e: DocumentEvent) {
        val currentTextEmpty = e.document.getText(0, e.document.length).isNullOrBlank()
        if (currentTextEmpty != previousTextEmpty) {
          previousTextEmpty = currentTextEmpty
          repaint()
        }
      }
    }

    private val ghostTextDocumentListener = GhostTextDocumentListener()

    init {
      Disposer.register(this@EditCommandPrompt, this@GhostTextField)

      addFocusListener(this)
      document.addDocumentListener(ghostTextDocumentListener)

      lineWrap = true
      wrapStyleWord = true
      border = JBUI.Borders.empty()
    }

    override fun paintComponent(g: Graphics) {
      background = textFieldBackground()
      (g as Graphics2D).background = textFieldBackground()
      super.paintComponent(g)

      if (text.isNullOrBlank()) {
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

    // Focus tracking ensures the ghost text is hidden or shown on focus change.
    // The superclass has a tendency to hide the text when we lose the focus.
    override fun focusGained(e: FocusEvent?) {
      repaint()
    }

    override fun focusLost(e: FocusEvent?) {
      repaint()
    }

    override fun dispose() {
      removeFocusListener(this)
      document.removeDocumentListener(ghostTextDocumentListener)
    }
  } // GhostTextField

  private fun makeCornerShape(width: Int, height: Int): RoundRectangle2D {
    return RoundRectangle2D.Double(
        0.0, 0.0, width.toDouble(), height.toDouble(), CORNER_RADIUS, CORNER_RADIUS)
  }

  override fun dispose() {
    if (!isDisposed.get()) {
      unregisterListeners()
      isDisposed.set(true)
    }
  }

  private fun onThemeChange() {
    SwingUtilities.invokeLater {
      // These all have their own backgrounds, so we manage them ourselves.
      dropdownSpacer.background = textFieldBackground()
      dropdownParent.background = textFieldBackground()
      titleLabel.foreground = boldLabelColor()
      revalidate()
      repaint()
    }
  }

  // A panel that pretends to be part of the TextField, below it, giving it
  // the appearance of having the llm dropdown be "within" the TextField.
  inner class TextAreaPanel : JPanel() {
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

  private fun getResizeDirection(point: Point): ResizeDirection? {
    val border = RESIZE_BORDER
    if (point.x < border) {
      return if (point.y < border) ResizeDirection.NORTH_WEST
      else if (point.y >= height - border) ResizeDirection.SOUTH_WEST else ResizeDirection.WEST
    } else if (point.x >= width - border) {
      return if (point.y < border) ResizeDirection.NORTH_EAST
      else if (point.y >= height - border) ResizeDirection.SOUTH_EAST else ResizeDirection.EAST
    } else if (point.y < border) {
      return ResizeDirection.NORTH
    } else if (point.y >= height - border) {
      return ResizeDirection.SOUTH
    }
    return null
  }

  private enum class ResizeDirection(val isHorizontal: Boolean, val isVertical: Boolean) {
    NORTH_WEST(false, true),
    NORTH(true, true),
    NORTH_EAST(true, true),
    WEST(false, true),
    EAST(true, true),
    SOUTH_WEST(false, false),
    SOUTH(true, false),
    SOUTH_EAST(true, false)
  }

  // TODO: Was hoping this would help avoid flicker while resizing.
  // Needs more work, but I think this is likely the right general approach.
  @Suppress("deprecation")
  class DoubleBufferedRootPane : JRootPane() {
    private var offscreenImage: BufferedImage? = null

    override fun paintComponent(g: Graphics) {
      if (offscreenImage == null ||
          offscreenImage!!.width != width ||
          offscreenImage!!.height != height) {
        offscreenImage = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB)
      }
      val offscreenGraphics = offscreenImage!!.createGraphics()
      super.paintComponent(offscreenGraphics)
      offscreenGraphics.dispose()

      (g as Graphics2D).drawImage(offscreenImage, 0, 0, null)
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

    private const val RESIZE_BORDER = 5

    private const val HISTORY_CAPACITY = 100
    val promptHistory = HistoryManager<String>(HISTORY_CAPACITY)

    // The last text the user typed in without saving it, for continuity.
    var lastPrompt: String = ""

    // Caching these caused problems with theme switches, even when we
    // updated the cached values on theme-switch notifications.
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

    /** Returns a compact symbol representation of the action's keyboard shortcut, if any. */
    @JvmStatic
    fun getShortcutText(actionId: String): String? {
      // If the keystroke has a registered shortcut, use that text.
      val action = ActionManager.getInstance().getAction(actionId)
      action?.shortcutSet?.shortcuts?.forEach { shortcut ->
        if (shortcut is KeyboardShortcut) {
          // This will return the shortcut in a format suitable for display,
          // including the correct symbols for the current OS.
          return KeymapUtil.getShortcutText(shortcut)
        }
      }
      // We have a few actions that share the same keystroke, because
      // they are similar operations in different contexts. They cannot be
      // registered in plugin.xml directly, because of collisions; instead,
      // we create intermediate actions to dispatch based on the mode.
      // Here, we just have to hardwire what we know the original sequence is.
      // TODO: There must be a way to convert a KeyStroke to these programmatically.
      // IntelliJ's Settings keymap viewer/editor shows them.
      return when (actionId) {
        "cody.editCodeAction",
        "cody.inlineEditRetryAction" ->
            getKeyStrokeDisplayString(
                KeyStroke.getKeyStroke(
                    KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
        "cody.inlineEditCancelAction",
        "cody.inlineEditUndoAction" ->
            getKeyStrokeDisplayString(
                KeyStroke.getKeyStroke(
                    KeyEvent.VK_BACK_SPACE,
                    InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK))
        else -> null
      }
    }

    private fun getKeyStrokeDisplayString(keyStroke: KeyStroke): String {
      val sb = StringBuilder()

      if (keyStroke.modifiers and InputEvent.CTRL_DOWN_MASK != 0) {
        sb.append("^")
      }
      if (keyStroke.modifiers and InputEvent.META_DOWN_MASK != 0) {
        sb.append("⌘")
      }
      if (keyStroke.modifiers and InputEvent.ALT_DOWN_MASK != 0) {
        sb.append("⌥")
      }
      if (keyStroke.modifiers and InputEvent.SHIFT_DOWN_MASK != 0) {
        sb.append("⇧")
      }

      when (keyStroke.keyCode) {
        KeyEvent.VK_ENTER -> sb.append("⏎")
        KeyEvent.VK_BACK_SPACE -> sb.append("⌫")
        else -> sb.append(KeyEvent.getKeyText(keyStroke.keyCode))
      }

      return sb.toString()
    }
  }
}
