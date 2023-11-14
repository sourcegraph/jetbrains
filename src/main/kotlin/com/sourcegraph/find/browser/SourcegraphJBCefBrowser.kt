package com.sourcegraph.find.browser

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.sourcegraph.cody.config.notification.AccountSettingChangeListener
import com.sourcegraph.cody.config.notification.CodySettingChangeListener
import com.sourcegraph.config.ThemeUtil
import javax.swing.UIManager
import org.cef.CefApp

class SourcegraphJBCefBrowser(requestHandler: JSToJavaBridgeRequestHandler) :
    JBCefBrowser("http://sourcegraph/html/index.html") {
  val javaToJSBridge: JavaToJSBridge

  init {
    // Create and set up JCEF browser
    CefApp.getInstance()
        .registerSchemeHandlerFactory("http", "sourcegraph", HttpSchemeHandlerFactory())

    // Create bridges, set up handlers, then run init function
    val initJSCode = "window.initializeSourcegraph();"
    val jsToJavaBridge = JSToJavaBridge(this, requestHandler, initJSCode)
    Disposer.register(this, jsToJavaBridge)
    javaToJSBridge = JavaToJSBridge(this)
    requestHandler.project.service<AccountSettingChangeListener>().javaToJSBridge = javaToJSBridge
    requestHandler.project.service<CodySettingChangeListener>().javaToJSBridge = javaToJSBridge
    UIManager.addPropertyChangeListener { propertyChangeEvent ->
      if (propertyChangeEvent.propertyName == "lookAndFeel") {
        javaToJSBridge.callJS("themeChanged", ThemeUtil.getCurrentThemeAsJson())
      }
    }
  }
}
