package com.sourcegraph.find

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupBorder
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.sourcegraph.Icons
import com.sourcegraph.find.browser.BrowserAndLoadingPanel
import com.sourcegraph.find.browser.BrowserAndLoadingPanel.ConnectionAndAuthState
import com.sourcegraph.find.browser.JSToJavaBridgeRequestHandler
import com.sourcegraph.find.browser.JavaToJSBridge
import com.sourcegraph.find.browser.SourcegraphJBCefBrowser
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import java.util.*
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import org.jdesktop.swingx.util.OS

/**
 * Inspired by
 * [FindPopupPanel.java](https://sourcegraph.com/github.com/JetBrains/intellij-community/-/blob/platform/lang-impl/src/com/intellij/find/impl/FindPopupPanel.java)
 */
class FindPopupPanel(project: Project, findService: FindService) : BorderLayoutPanel(), Disposable {
  private val browser: SourcegraphJBCefBrowser?
  val previewPanel: PreviewPanel = PreviewPanel(project)
  private val browserAndLoadingPanel = BrowserAndLoadingPanel(project)
  private val selectionMetadataPanel = SelectionMetadataPanel()
  private val footerPanel: FooterPanel = FooterPanel()
  private var lastPreviewUpdate: Date

  init {
    preferredSize = JBUI.size(1000, 700)
    setBorder(PopupBorder.Factory.create(true, true))
    setFocusCycleRoot(true)
    val splitter = OnePixelSplitter(true, 0.5f, 0.1f, 0.9f)
    this.add(splitter, BorderLayout.CENTER)
    val bottomPanel =
        BorderLayoutPanel().apply {
          add(selectionMetadataPanel, BorderLayout.NORTH)
          add(previewPanel, BorderLayout.CENTER)
          add(footerPanel, BorderLayout.SOUTH)
        }
    val requestHandler = JSToJavaBridgeRequestHandler(project, this, findService)
    browser = if (JBCefApp.isSupported()) SourcegraphJBCefBrowser(requestHandler) else null
    if (browser == null) {
      showNoBrowserErrorNotification(project)
      val logger = Logger.getInstance(JSToJavaBridgeRequestHandler::class.java)
      logger.warn("JCEF browser is not supported!")
    } else {
      browserAndLoadingPanel.setBrowser(browser)
    }
    // The border is needed on macOS because without it, window and splitter resize don't work
    // because the JCEF
    // doesn't properly pass the mouse events to Swing.
    // 4px is the minimum amount to make it work for the window resize, the splitter works without a
    // padding.
    val browserContainerForOptionalBorder = JPanel(BorderLayout())
    if (OS.isMacOSX()) {
      browserContainerForOptionalBorder.setBorder(JBUI.Borders.empty(0, 4, 5, 4))
    }
    browserContainerForOptionalBorder.add(browserAndLoadingPanel, BorderLayout.CENTER)

    val headerPanel = HeaderPanel()
    val topPanel =
        BorderLayoutPanel().apply {
          add(headerPanel, BorderLayout.NORTH)
          add(browserContainerForOptionalBorder, BorderLayout.CENTER)
          minimumSize = JBUI.size(750, 200)
        }
    splitter.firstComponent = topPanel
    splitter.secondComponent = bottomPanel
    lastPreviewUpdate = Date()
    UIManager.addPropertyChangeListener { propertyChangeEvent ->
      if (propertyChangeEvent.propertyName == "lookAndFeel") {
        SwingUtilities.updateComponentTreeUI(this)
      }
    }
  }

  private fun showNoBrowserErrorNotification(project: Project) {
    val notification =
        Notification(
            "Sourcegraph errors",
            "Sourcegraph",
            "Your IDE doesn't support JCEF. You won't be able to use \"Find with Sourcegraph\". If you believe this is an error, please raise this at support@sourcegraph.com, specifying your OS and IDE version.",
            NotificationType.ERROR)
    notification.setIcon(Icons.CodyLogo)
    notification.addAction(
        object : DumbAwareAction("Copy Support Email Address") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            CopyPasteManager.getInstance().setContents(StringSelection("support@sourcegraph.com"))
            notification.expire()
          }
        })
    notification.addAction(
        object : DumbAwareAction("Dismiss") {
          override fun actionPerformed(anActionEvent: AnActionEvent) {
            notification.expire()
          }
        })
    notification.notify(project)
  }

  val javaToJSBridge: JavaToJSBridge?
    get() = browser?.javaToJSBridge

  val connectionAndAuthState: ConnectionAndAuthState
    get() = browserAndLoadingPanel.connectionAndAuthState

  fun browserHasSearchError(): Boolean = browserAndLoadingPanel.hasSearchError()

  fun indicateAuthenticationStatus(wasServerAccessSuccessful: Boolean, authenticated: Boolean) {
    browserAndLoadingPanel.connectionAndAuthState = when {
      wasServerAccessSuccessful && authenticated -> ConnectionAndAuthState.AUTHENTICATED
      wasServerAccessSuccessful && !authenticated -> ConnectionAndAuthState.COULD_CONNECT_BUT_NOT_AUTHENTICATED
      else -> ConnectionAndAuthState.COULD_NOT_CONNECT
    }

    if (wasServerAccessSuccessful) {
      previewPanel.setState(PreviewPanel.State.PREVIEW_AVAILABLE)
      footerPanel.setPreviewContent(previewPanel.previewContent)
    } else {
      selectionMetadataPanel.clearSelectionMetadataLabel()
      previewPanel.setState(PreviewPanel.State.NO_PREVIEW_AVAILABLE)
      footerPanel.setPreviewContent(null)
    }
  }

  fun indicateSearchError(errorMessage: String, date: Date) {
    if (lastPreviewUpdate.before(date)) {
      lastPreviewUpdate = date
      browserAndLoadingPanel.setBrowserSearchErrorMessage(errorMessage)
      selectionMetadataPanel.clearSelectionMetadataLabel()
      previewPanel.setState(PreviewPanel.State.NO_PREVIEW_AVAILABLE)
      footerPanel.setPreviewContent(null)
    }
  }

  fun indicateLoadingIfInTime(date: Date) {
    if (lastPreviewUpdate.before(date)) {
      lastPreviewUpdate = date
      selectionMetadataPanel.clearSelectionMetadataLabel()
      previewPanel.setState(PreviewPanel.State.LOADING)
      footerPanel.setPreviewContent(null)
    }
  }

  fun setPreviewContentIfInTime(previewContent: PreviewContent) {
    if (lastPreviewUpdate.before(previewContent.receivedDateTime)) {
      lastPreviewUpdate = previewContent.receivedDateTime
      browserAndLoadingPanel.setBrowserSearchErrorMessage(null)
      selectionMetadataPanel.setSelectionMetadataLabel(previewContent)
      previewPanel.setContent(previewContent)
      footerPanel.setPreviewContent(previewContent)
    }
  }

  fun clearPreviewContentIfInTime(date: Date) {
    if (lastPreviewUpdate.before(date)) {
      lastPreviewUpdate = date
      browserAndLoadingPanel.setBrowserSearchErrorMessage(null)
      selectionMetadataPanel.clearSelectionMetadataLabel()
      previewPanel.setState(PreviewPanel.State.NO_PREVIEW_AVAILABLE)
      footerPanel.setPreviewContent(null)
    }
  }

  override fun dispose() {
    browser?.dispose()
    previewPanel.dispose()
  }
}
