package com.sourcegraph.find

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.ui.UIUtil
import com.sourcegraph.find.browser.BrowserAndLoadingPanel
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent

class FindService(private val project: Project) : Disposable {
  // Create main panel
  private val mainPanel = FindPopupPanel(project, this)
  private var popup: FindPopupDialog? = null

  @Synchronized
  fun showPopup() {
    createOrShowPopup()
  }

  fun hidePopup() {
    popup?.hide()
    hideMaterialUiOverlay()
  }

  private fun createOrShowPopup() {
    if (popup != null) {
      val popup = popup ?: return
      if (!popup.isVisible) {
        popup.show()

        // Retry auth and search if the popup is in a possible connection error state
        if (mainPanel.browserHasSearchError() ||
            mainPanel.connectionAndAuthState ==
                BrowserAndLoadingPanel.ConnectionAndAuthState.COULD_NOT_CONNECT) {
          val bridge = mainPanel.javaToJSBridge
          bridge?.callJS("retrySearch", null)
        }
      }
    } else {
      popup = FindPopupDialog(project, mainPanel)

      // We add a manual listener to the global key handler since the editor component seems to work
      // around the
      // default Swing event handler.
      registerGlobalKeyListeners()

      // We also need to detect when the main IDE frame or another popup inside the project gets
      // focus and close
      // the Sourcegraph window accordingly.
      registerOutsideClickListener()
    }
  }

  private fun registerGlobalKeyListeners() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
      if (e.id != KeyEvent.KEY_PRESSED || popup?.isDisposed == true || popup?.isVisible == false) {
        return@addKeyEventDispatcher false
      }
      handleKeyPress(e.keyCode, e.modifiersEx)
    }
  }

  private fun handleKeyPress(keyCode: Int, modifiers: Int): Boolean {
    if (keyCode == KeyEvent.VK_ESCAPE && modifiers == 0) {
      ApplicationManager.getApplication().invokeLater { hidePopup() }
      return true
    }
    if (keyCode == KeyEvent.VK_ENTER &&
        modifiers and InputEvent.ALT_DOWN_MASK == InputEvent.ALT_DOWN_MASK) {
      val mainPanelPreviewContent = mainPanel.previewPanel.previewContent ?: return false
      // This must run on EDT (Event Dispatch Thread) because it may interact with the editor.
      ApplicationManager.getApplication().invokeLater {
        try {
          mainPanelPreviewContent.openInEditorOrBrowser()
        } catch (e: Exception) {
          logger.warn("Error opening file in editor", e)
        }
      }
      return true
    }
    return false
  }

  private fun registerOutsideClickListener() {
    val projectParentWindow = getParentWindow(null)
    Toolkit.getDefaultToolkit()
        .addAWTEventListener(
            { event ->
              if (event is WindowEvent) {
                // We only care for focus events
                if (event.id != WindowEvent.WINDOW_GAINED_FOCUS) {
                  return@addAWTEventListener
                }
                if (popup?.isVisible == false) {
                  return@addAWTEventListener
                }

                // Detect if we're focusing the Sourcegraph popup
                if (event.component == popup?.window) {
                  return@addAWTEventListener
                }

                // Detect if the newly focused window is a parent of the project root window
                val currentProjectParentWindow = getParentWindow(event.component)
                if (currentProjectParentWindow == projectParentWindow) {
                  hidePopup()
                }
              }
            },
            AWTEvent.WINDOW_EVENT_MASK)
  }

  // https://sourcegraph.com/github.com/JetBrains/intellij-community@27fee7320a01c58309a742341dd61deae57c9005/-/blob/platform/platform-impl/src/com/intellij/ui/popup/AbstractPopup.java?L475-493
  private fun getParentWindow(component: Component?): Window? {
    var window: Window? = null
    val parent =
        UIUtil.findUltimateParent(
            component ?: WindowManagerEx.getInstanceEx().getFocusedComponent(project))
    if (parent is Window) {
      window = parent
    }
    return window ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
  }

  override fun dispose() {
    popup?.window?.dispose()
    mainPanel.dispose()
  }

  // We manually emit an action defined by the material UI theme to hide the overlay it opens
  // whenever a popover is
  // created. This third-party plugin does not work with our approach of keeping the popover alive
  // and thus, when the
  // Sourcegraph popover is closed, their custom overlay stays active.
  //
  //   - https://github.com/sourcegraph/sourcegraph/issues/36479
  //   - https://github.com/mallowigi/material-theme-issues/issues/179
  private fun hideMaterialUiOverlay() {
    val materialAction = ActionManager.getInstance().getAction("MTToggleOverlaysAction") ?: return
    try {
      val dataContext =
          DataManager.getInstance().dataContextFromFocusAsync.blockingGet(10) ?: return
      materialAction.actionPerformed(
          AnActionEvent(
              null,
              dataContext,
              ActionPlaces.UNKNOWN,
              Presentation(),
              ActionManager.getInstance(),
              0))
    } catch (ignored: Exception) {}
  }

  companion object {
    private val logger = Logger.getInstance(FindService::class.java)
  }
}
