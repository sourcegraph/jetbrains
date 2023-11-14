package com.sourcegraph.find

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapperPeer
import com.intellij.openapi.ui.DialogWrapperPeerFactory
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.WindowResizeListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.border.Border

class FindPopupDialog(private val project: Project, private val mainPanel: JComponent) :
    DialogWrapper(project, false) {
  init {
    title = "Find with Sourcegraph"
    window.minimumSize = Dimension(750, 420)
    init()
    addNativeFindInFilesBehaviors()

    // Avoid the show method to be blocking
    this.isModal = false
    // Prevent the dialog from being cancelable by any default behaviors
    myCancelAction.isEnabled = false
    super.show()
  }

  // Overwrite the createPeer function that is being used in the super constructor so that we can
  // create a new frame.
  // This is needed because the frame is otherwise shared with native overlays like Find in Files or
  // Search Everywhere
  // which can lead to race conditions when some other overlays are opened while the Sourcegraph
  // window is open.
  //
  // A new frame prevents us from running into the issue since it will never be shared with any
  // other view.
  //
  // This frame behaves slightly different to standard project frames though: Some menu options will
  // be greyed out
  // like e.g. the option to open Search Everywhere.
  override fun createPeer(
      project: Project?,
      canBeParent: Boolean,
      ideModalityType: IdeModalityType
  ): DialogWrapperPeer {
    val frame = Frame()
    return DialogWrapperPeerFactory.getInstance()
        .createPeer(this, frame, canBeParent, ideModalityType)
  }

  override fun createCenterPanel(): JComponent = mainPanel

  // This adds behaviors found in JetBrain's native FindPopupPanel:
  // https://sourcegraph.com/github.com/JetBrains/intellij-community/-/blob/platform/lang-impl/src/com/intellij/find/impl/FindPopupPanel.java
  private fun addNativeFindInFilesBehaviors() {
    setUndecorated(true)
    ApplicationManager.getApplication()
        .messageBus
        .connect(this.disposable)
        .subscribe(
            ProjectManager.TOPIC,
            object : ProjectManagerListener {
              override fun projectClosed(project: Project) {
                this@FindPopupDialog.doCancelAction()
              }
            })
    val window = WindowManager.getInstance().suggestParentWindow(project)
    val parent = UIUtil.findUltimateParent(window)
    var showPoint: RelativePoint? = null
    val screenPoint = DimensionService.getInstance().getLocation(SERVICE_KEY, project)
    if (screenPoint != null) {
      showPoint =
          if (parent != null) {
            SwingUtilities.convertPointFromScreen(screenPoint, parent)
            RelativePoint(parent, screenPoint)
          } else {
            RelativePoint(screenPoint)
          }
    }
    if (parent != null && showPoint == null) {
      var height = if (UISettings.getInstance().showNavigationBar) 135 else 115
      if (parent is IdeFrame && (parent as IdeFrame).isInFullScreen) {
        height -= 20
      }
      showPoint =
          RelativePoint(parent, Point((parent.size.width - preferredSize.width) / 2, height))
    }

    addMoveListeners(mainPanel)
    val panelSize = preferredSize
    val prev = DimensionService.getInstance().getSize(SERVICE_KEY, project)
    if (prev != null && prev.height < panelSize.height) prev.height = panelSize.height

    val dialogWindow = this.peer.window
    val root = (dialogWindow as RootPaneContainer).rootPane
    val glass = this.rootPane.glassPane as IdeGlassPaneImpl

    val resizeListener: WindowResizeListener =
        object : WindowResizeListener(root, JBUI.insets(4), null) {
          private var myCursor: Cursor? = null

          override fun setCursor(content: Component, cursor: Cursor) {
            if (myCursor !== cursor || myCursor !== Cursor.getDefaultCursor()) {
              glass.setCursor(cursor, this)
              myCursor = cursor
              if (content is JComponent) {
                IdeGlassPaneImpl.savePreProcessedCursor(content, content.getCursor())
              }
              super.setCursor(content, cursor)
            }
          }
        }

    glass.addMousePreprocessor(resizeListener, myDisposable)
    glass.addMouseMotionPreprocessor(resizeListener, myDisposable)

    root.setWindowDecorationStyle(JRootPane.NONE)
    root.setBorder(PopupBorder.Factory.create(true, true))

    UIUtil.markAsPossibleOwner(dialogWindow as Dialog)
    dialogWindow.setBackground(UIUtil.getPanelBackground())
    dialogWindow.setMinimumSize(panelSize)
    dialogWindow.setSize(prev ?: panelSize)

    IdeEventQueue.getInstance().popupManager.closeAllPopups(false)
    if (showPoint != null) {
      this.setLocation(showPoint.getScreenPoint())
    } else {
      dialogWindow.setLocationRelativeTo(null)
    }
  }

  private fun addMoveListeners(component: Component) {
    val windowListener = WindowMoveListener(component)
    component.addMouseListener(windowListener)
    component.addMouseMotionListener(windowListener)
  }

  override fun createSouthPanel(): JComponent? = null

  override fun createContentPaneBorder(): Border? = null

  fun hide() {
    saveDimensions()
    peer.window.isVisible = false
  }

  override fun show() {
    peer.window.isVisible = true

    // When the dialog is forced to be visible again, it's dimensions are reset to what they were
    // originally set
    // when it first opened. Since we do have a snapshot of the locations saved from the time we hid
    // it which was
    // captured with the current state of the content, we can apply simply it.
    applyDimensions()
  }

  // The automatic size saving behavior for DialogWrapper does not work for us as it relies on
  // disposing of the
  // dialog to persist the changes. We need to manually implement this behavior instead.
  private fun saveDimensions() {
    val windowStateService = WindowStateService.getInstance(project)
    val location = location
    val size = size
    windowStateService.putLocation(SERVICE_KEY, location)
    windowStateService.putSize(SERVICE_KEY, size)
  }

  private fun applyDimensions() {
    val windowStateService = WindowStateService.getInstance(project)
    val location = windowStateService.getLocation(SERVICE_KEY)
    val size = windowStateService.getSize(SERVICE_KEY)
    setLocation(location)
    setSize(size.width, size.height)
  }

  override fun getDimensionServiceKey(): String = SERVICE_KEY

  companion object {
    private const val SERVICE_KEY = "sourcegraph.find.popup"
  }
}
