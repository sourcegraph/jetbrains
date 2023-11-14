package com.sourcegraph.find.browser

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.sourcegraph.cody.config.ui.AccountConfigurable
import org.apache.commons.lang.SystemUtils
import org.apache.commons.lang.WordUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.JLayeredPane

/**
 * Inspired by [FindPopupPanel.java](https://sourcegraph.com/github.com/JetBrains/intellij-community/-/blob/platform/lang-impl/src/com/intellij/find/impl/FindPopupPanel.java)
 */
class BrowserAndLoadingPanel(private val project: Project): JLayeredPane() {
  private val overlayPanel: JBPanelWithEmptyText = JBPanelWithEmptyText()
  private val jcefPanel: JBPanelWithEmptyText = JBPanelWithEmptyText(BorderLayout())
    .withEmptyText(
      "Unfortunately, the browser is not available on your system. Try running the IDE with the default OpenJDK."
    )
  private var isBrowserVisible = false
  private var connectionAndAuthState = ConnectionAndAuthState.LOADING
  private var errorMessage: String? = null

  init {
    setConnectionAndAuthState(ConnectionAndAuthState.LOADING)

    // We need to use the add(Component, Object) overload of the add method to ensure that the
    // constraints are
    // properly set.
    add(overlayPanel, 1)
    add(jcefPanel, 2)
  }

  fun setBrowserSearchErrorMessage(errorMessage: String?) {
    this.errorMessage = errorMessage
    refreshUI()
  }

  fun setConnectionAndAuthState(state: ConnectionAndAuthState) {
    connectionAndAuthState = state
    refreshUI()
  }

  private fun refreshUI() {
    val emptyText = overlayPanel.emptyText
    isBrowserVisible = (errorMessage == null
            && (connectionAndAuthState == ConnectionAndAuthState.AUTHENTICATED
            || connectionAndAuthState
            == ConnectionAndAuthState.COULD_CONNECT_BUT_NOT_AUTHENTICATED))
    if (connectionAndAuthState == ConnectionAndAuthState.COULD_NOT_CONNECT) {
      emptyText.text = "Could not connect to Sourcegraph."
      emptyText.appendLine(
        "Make sure your Sourcegraph URL and access token are correct to use search."
      )
      emptyText.appendLine(
        "Click here to configure your Sourcegraph Cody + Code Search settings.",
        SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.Foreground.ENABLED)
      ) { _ ->
        ShowSettingsUtil.getInstance()
          .showSettingsDialog(project, AccountConfigurable::class.java)
      }
    } else if (errorMessage != null) {
      val wrappedText = WordUtils.wrap("Error: $errorMessage", 100)
      val lines = wrappedText.split(SystemUtils.LINE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
        .toTypedArray()
      emptyText.text = lines[0]
      for (i in 1 until lines.size) {
        if (lines[i].trim { it <= ' ' }.isNotEmpty()) {
          emptyText.appendLine(lines[i])
        }
      }
      emptyText.appendLine("")
      emptyText.appendLine(
        "If you believe this is a bug, please raise this at support@sourcegraph.com,"
      )
      emptyText.appendLine(
        "mentioning the above error message and your Cody plugin and Cody App or Sourcegraph server version."
      )
      emptyText.appendLine("Sorry for the inconvenience.")
    } else if (connectionAndAuthState == ConnectionAndAuthState.LOADING) {
      emptyText.text = "Loading..."
    } else {
      // We need to do this because the "COULD_NOT_CONNECT" link is clickable even when the empty
      // text is hidden! :o
      emptyText.text = ""
    }
    revalidate()
    repaint()
  }

  fun getConnectionAndAuthState(): ConnectionAndAuthState = connectionAndAuthState

  fun hasSearchError(): Boolean = errorMessage != null

  fun setBrowser(browser: SourcegraphJBCefBrowser) {
    jcefPanel.add(browser.component)
  }

  override fun doLayout() {
    if (isBrowserVisible) {
      jcefPanel.setBounds(0, 0, width, height)
    } else {
      jcefPanel.setBounds(0, 0, 1, 1)
    }
    overlayPanel.setBounds(0, 0, width, height)
  }

  override fun getPreferredSize(): Dimension = bounds.size

  enum class ConnectionAndAuthState {
    LOADING,
    AUTHENTICATED,
    COULD_NOT_CONNECT,
    COULD_CONNECT_BUT_NOT_AUTHENTICATED
  }
}
