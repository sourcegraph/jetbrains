package com.sourcegraph.cody.sidebar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.io.isAncestor
import com.sourcegraph.cody.agent.CodyAgent
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefAuthCallback
import org.cef.callback.CefCallback
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefCookie
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.cef.network.CefURLRequest
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.math.min

// We make up a host name and serve the static resources into the webview apparently from this host.
val PSEUDO_HOST = "file+.sourcegraphstatic.com"
val PSEUDO_HOST_URL_PREFIX = "https://$PSEUDO_HOST/"
// VSCode does this differently and uses a cross-origin request for subresources.
// This requires rewriting relative URLs, so for now stick everything on the HTTPS + pseudo host origin.
val MAIN_RESOURCE_URL = "${PSEUDO_HOST_URL_PREFIX}webviews/index.html" // "cody:///webviews/index.html"

class WebUIChatWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    createWindow(project, toolWindow)
  }

  private fun createWindow(project: Project, toolWindow: ToolWindow) {
    val browser = JBCefBrowserBuilder().apply {
      setOffScreenRendering(false)
      // TODO: Make this conditional on running in a debug configuration.
      setEnableOpenDevToolsMenuItem(true)
    }.build()

    val viewToHost = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
      addHandler { query: String ->
        println("webview -> host: $query")
        JBCefJSQuery.Response(null)
      }
    }
    val apiScript = """
      globalThis.acquireVsCodeApi = (function() {
          let acquired = false;
          let state = undefined;

          return () => {
              if (acquired && !false) {
                  throw new Error('An instance of the VS Code API has already been acquired');
              }
              acquired = true;
              return Object.freeze({
                  postMessage: function(message, transfer) {
                    console.assert(!transfer);
                    ${viewToHost.inject("JSON.stringify(message)")}
                  },
                  setState: function(newState) {
                      state = newState;
                      // doPostMessage('do-update-state', JSON.stringify(newState));
                      return newState;
                  },
                  getState: function() {
                      return state;
                  }
              });
          };
      })();
      delete window.parent;
      delete window.top;
      delete window.frameElement;
    """.trimIndent()
    println(viewToHost.inject("", "bar", "baz"))
    browser.jbCefClient.addRequestHandler(ExtensionRequestHandler(apiScript), browser.cefBrowser)

    // TODO: What is "lockable" content?
    val lockable = false
    toolWindow.contentManager.addContent(
      ContentFactory.SERVICE.getInstance().createContent(browser.component, "TODO: Content Display Name", lockable)
    )

    browser.loadURL(MAIN_RESOURCE_URL)
  }
}

class ExtensionRequestHandler(private val apiScript: String) : CefRequestHandler {
  override fun onBeforeBrowse(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?,
    userGesture: Boolean,
    isRedirect: Boolean
  ): Boolean {
    // TODO: Consider blocking navigations away from the extension.
    return false
  }

  override fun onOpenURLFromTab(
    browser: CefBrowser?,
    frame: CefFrame?,
    targetUrl: String?,
    userGesture: Boolean
  ): Boolean {
    // TODO: Add Telemetry
    // We don't support tabbed browsing so cancel navigation.
    return true
  }

  override fun getResourceRequestHandler(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?,
    isNavigation: Boolean,
    isDownload: Boolean,
    requestInitiator: String?,
    disableDefaultHandling: BoolRef?
  ): CefResourceRequestHandler? {
    if (request?.url == MAIN_RESOURCE_URL || request?.url?.startsWith(PSEUDO_HOST_URL_PREFIX) == true) {
      disableDefaultHandling?.set(true)
      return ExtensionResourceRequestHandler(apiScript)
    }
    disableDefaultHandling?.set(false)
    return null
  }

  override fun getAuthCredentials(
    browser: CefBrowser?,
    originUrl: String?,
    isProxy: Boolean,
    host: String?,
    port: Int,
    realm: String?,
    scheme: String?,
    callback: CefAuthCallback?
  ): Boolean {
    // We do not load web content that requires authentication.
    return false
  }

  override fun onQuotaRequest(
    browser: CefBrowser?,
    originUrl: String?,
    newSize: Long,
    callback: CefCallback?
  ): Boolean {
    // TODO: Filter to the extension origin.
    callback?.Continue()
    return true
  }

  override fun onCertificateError(
    browser: CefBrowser?,
    certError: CefLoadHandler.ErrorCode?,
    requestUrl: String?,
    callback: CefCallback?
  ): Boolean {
    // TODO: Add Telemetry here.
    return false
  }

  override fun onPluginCrashed(browser: CefBrowser?, pluginPath: String?) {
    // TODO: Add Telemetry here.
    // As we do not use plugins, we do not need to handle this.
  }

  override fun onRenderProcessTerminated(browser: CefBrowser?, status: CefRequestHandler.TerminationStatus?) {
    // TODO: Add Telemetry here.
    // TODO: Logging.
    // TODO: Trigger a reload.
  }
}

class ExtensionResourceRequestHandler(private val apiScript: String) : CefResourceRequestHandler {
  override fun getCookieAccessFilter(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?
  ): CefCookieAccessFilter {
    // TODO: Make this a single object.
    return object : CefCookieAccessFilter {
      override fun canSaveCookie(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest?,
        response: CefResponse?,
        cookie: CefCookie?
      ): Boolean {
        // We do not load web content that uses cookies, so block them all.
        return false
      }

      override fun canSendCookie(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest?,
        cookie: CefCookie?
      ): Boolean {
        // We do not load web content that uses cookies, so there are no cookies to send.
        return false
      }
    }
  }

  override fun onBeforeResourceLoad(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?): Boolean {
    return false
  }

  override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?): CefResourceHandler {
    return ExtensionResourceHandler(apiScript)
  }

  override fun onResourceRedirect(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?,
    response: CefResponse?,
    newUrl: StringRef?
  ) {
    // We do not serve redirects.
    TODO("unreachable")
  }

  override fun onResourceResponse(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?,
    response: CefResponse?
  ): Boolean {
    return false
  }

  override fun onResourceLoadComplete(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?,
    response: CefResponse?,
    status: CefURLRequest.Status?,
    receivedContentLength: Long
  ) {
    // No-op
  }

  override fun onProtocolExecution(
    browser: CefBrowser?,
    frame: CefFrame?,
    request: CefRequest?,
    allowOsExecution: BoolRef?
  ) {
    TODO("Not yet implemented")
  }
}

class ExtensionResourceHandler(private val apiScript: String) : CefResourceHandler {
  private val logger = Logger.getInstance(ExtensionResourceHandler::class.java)
  var status = 0
  var bytesReadFromResource = 0L
  private var bytesSent = 0L
  private var bytesWaitingSend = ByteBuffer.allocate(100).flip() // TODO: increase this, just testing we handle exceeding the buffer capacity correctly
  private var contentLength = 0L
  var contentType = "text/plain"
  var readChannel: AsynchronousFileChannel? = null

  // Some response bodies need to be rewritten. Gets the rewriter, if any, for the specified request path.
  private fun rewriterForRequestPath(requestPath: String): ((content: String) -> String)? =
      when {
          requestPath.endsWith(".html") -> { content: String ->
            // TODO: It is cheesy to look for <head> instead of parsing DOM content. But it is effective.
            content.replace("{cspSource}", "'self' https://*.sourcegraphstatic.com").replace("<head>", "<head><script>$apiScript</script>")
          }
          else -> null
      }

  override fun processRequest(request: CefRequest?, callback: CefCallback?): Boolean {
    val uri = URI(request?.url ?: return false)
    val requestPath = uri.path.removePrefix("/")
    ApplicationManager.getApplication().executeOnPooledThread {
      // Find the plugin resources.
      val codyDirOverride = System.getenv("CODY_DIR")
      val resourcesPath =
        if (codyDirOverride != null) {
          Path(codyDirOverride).resolve("vscode/dist")
        } else {
          CodyAgent.pluginDirectory()?.resolve("agent")
        }
      if (resourcesPath == null) {
        logger.warn("Aborting WebView request for ${requestPath}, extension resource directory not found found")
        status = 500
        callback?.Continue()
        return@executeOnPooledThread
      }

      // Find the specific file being requested.
      val filePath = resourcesPath.resolve(requestPath)
      if (!resourcesPath.isAncestor(filePath)) {
        logger.warn("Aborting WebView request for ${requestPath}, attempted directory traversal?")
        status = 400
        callback?.Continue()
        return@executeOnPooledThread
      }

      // Find the particulars of that file.
      val file = filePath.toFile()
      contentLength = file.length()
      contentType = when {
        requestPath.endsWith(".css") -> "text/css"
        requestPath.endsWith(".html") -> "text/html"
        requestPath.endsWith(".js") -> "text/javascript"
        requestPath.endsWith(".png") -> "image/png"
        requestPath.endsWith(".svg") -> "image/svg+xml"
        requestPath.endsWith(".ttf") -> "font/ttf"
        else -> "text/plain"
      }

      val rewriter = rewriterForRequestPath(requestPath)
      if (rewriter == null) {
        // Prepare to read the file contents.
        try {
          readChannel = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)
        } catch (e: IOException) {
          logger.warn("Failed to open file ${file.absolutePath} to serve extension WebView request $requestPath", e)
          status = 404
          callback?.Continue()
          return@executeOnPooledThread
        }
      } else {
        // Read and rewrite the file contents.
        val content = file.readText()
        val rewrittenContent = rewriter(content)
        bytesWaitingSend = ByteBuffer.wrap(rewrittenContent.toByteArray())
      }

      // We're ready to synthesize headers.
      status = 200
      callback?.Continue()
    }
    return true
  }

  override fun getResponseHeaders(response: CefResponse?, responseLength: IntRef?, redirectUrl: StringRef?) {
    response?.status = status
    response?.mimeType = contentType
    // TODO: Do we need to set content-encoding here?
    responseLength?.set(contentLength.toInt())
  }

  override fun readResponse(
    dataOut: ByteArray?,
    bytesToRead: Int,
    bytesRead: IntRef?,
    callback: CefCallback?
  ): Boolean {
    if (bytesSent >= contentLength || dataOut == null) {
      try {
        readChannel?.close()
      } catch (e: IOException) {
      }
      bytesRead?.set(0)
      return false
    }

    if (bytesWaitingSend.remaining() > 0) {
      val willSendNumBytes = min(bytesWaitingSend.remaining() as Int, bytesToRead)
      bytesWaitingSend.get(dataOut, 0, willSendNumBytes)
      bytesRead?.set(willSendNumBytes)
      return true
    } else {
      bytesWaitingSend.flip()
      bytesWaitingSend.limit(bytesWaitingSend.capacity())
    }

    if (readChannel == null) {
      // We need to read more, but the readChannel is closed.
      bytesRead?.set(0)
      return false
    }

    // Start an asynchronous read.
    readChannel?.read(bytesWaitingSend, bytesReadFromResource, null, object : CompletionHandler<Int, Void?> {
      override fun completed(result: Int, attachment: Void?) {
        if (result == -1) {
          try {
            readChannel?.close()
          } catch (e: IOException) {
          }
          readChannel = null
        } else {
          bytesReadFromResource += result
        }
        bytesWaitingSend.flip()
        callback?.Continue()
      }

      override fun failed(exc: Throwable?, attachment: Void?) {
        try {
          readChannel?.close()
        } catch (e: IOException) {
        }
        readChannel = null
        callback?.Continue()
      }
    })

    bytesRead?.set(0)
    return true
  }

  override fun cancel() {
    try {
      readChannel?.close()
    } catch (e: IOException) {
    }
    readChannel = null
  }
}
