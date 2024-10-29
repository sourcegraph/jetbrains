package com.sourcegraph.robot

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.JCefBrowserFixture
import com.intellij.remoterobot.search.locators.byXpath
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BasicWebViewTest {

  private val remoteRobot = RemoteRobot("http://127.0.0.1:8580")
  private val locator = byXpath("//div[@class='JBCefOsrComponent']")
  private val fixture = remoteRobot.find(JCefBrowserFixture::class.java, locator)

  private val JB_CEF_BROWSER_KEY = "__jbCefBrowser"
  private val CEF_BROWSER_KEY = "__cefBrowser"

  @Test
  fun `basic webview test`() {
    assertEquals("hello world", fixture.executeJsInBrowser("`hello world`"))
  }

  @Test
  fun `open devtools from jbcef`() {
    fixture.callJs<Boolean>(
        """
      const jbcefBrowser = local.get("${JB_CEF_BROWSER_KEY}")
      jbcefBrowser.openDevtools()
      true
    """
            .trimIndent())
  }

  @Test
  fun `execute CDP method`() {
    val browserVersion =
        fixture.callJs<String>(
            """
      const cefBrowser = local.get("${CEF_BROWSER_KEY}")
      cefBrowser.devToolsClient.executeDevToolsMethod("Browser.getVersion").get()
    """
                .trimIndent())
    assert(browserVersion.contains("userAgent"))
  }
}
