package com.sourcegraph.find.browser

import com.google.gson.JsonParser
import com.intellij.openapi.Disposable
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.network.CefRequest.TransitionType
import org.intellij.lang.annotations.Language

class JSToJavaBridge(
    browser: JBCefBrowserBase,
    requestHandler: JSToJavaBridgeRequestHandler,
    jsCodeToRunAfterBridgeInit: String?
) : Disposable {
  val query = JBCefJSQuery.create(browser)

  init {
    query.addHandler { requestAsString ->
      try {
        val requestAsJson = JsonParser.parseString(requestAsString).asJsonObject
        return@addHandler requestHandler.handle(requestAsJson)
      } catch (e: Exception) {
        return@addHandler requestHandler.handleInvalidRequest(e)
      }
    }

    val cefLoadHandler = object : CefLoadHandler {
      override fun onLoadStart(
          cefBrowser: CefBrowser,
          frame: CefFrame,
          transitionType: TransitionType
      ) {}

      override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        // In case of a failure, Java returns two arguments, so must use an intermediate
        // function.
        // (source:
        // https://dploeger.github.io/intellij-api-doc/com/intellij/ui/jcef/JBCefJSQuery.html#:~:text=onFailureCallback%20%2D%20JS%20callback%20in%20format%3A%20function(error_code%2C%20error_message)%20%7B%7D)
        @Language("javascript")
        val script = """
          window.callJava = function(request) {
              return new Promise((resolve, reject) => {
                  const requestAsString = JSON.stringify(request);
                  const onSuccessCallback = responseAsString => {
                      resolve(JSON.parse(responseAsString));
                  };
                  const onFailureCallback = (errorCode, errorMessage) => {
                      reject(new Error(`\${'$'}{errorCode} - \${'$'}{errorMessage}`));
                  };
                  
                  ${query.inject("requestAsString", "onSuccessCallback", "onFailureCallback")}
              });
          };
        """.trimIndent()
        cefBrowser.executeJavaScript(script, cefBrowser.url, 0)
        cefBrowser.executeJavaScript(jsCodeToRunAfterBridgeInit, "", 0)
      }

      override fun onLoadingStateChange(
          cefBrowser: CefBrowser,
          isLoading: Boolean,
          canGoBack: Boolean,
          canGoForward: Boolean
      ) {}

      override fun onLoadError(
          cefBrowser: CefBrowser,
          frame: CefFrame,
          errorCode: CefLoadHandler.ErrorCode,
          errorText: String,
          failedUrl: String
      ) {}
    }
    browser.jbCefClient.addLoadHandler(cefLoadHandler, browser.cefBrowser)
  }

  override fun dispose() = query.dispose()
}
