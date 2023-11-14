package com.sourcegraph.find.browser

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefSchemeHandlerFactory
import org.cef.handler.CefResourceHandler
import org.cef.network.CefRequest

class HttpSchemeHandlerFactory: CefSchemeHandlerFactory {
  override fun create(
    browser: CefBrowser, frame: CefFrame, schemeName: String, request: CefRequest
  ): CefResourceHandler = HttpSchemeHandler()
}
