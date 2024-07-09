package com.sourcegraph.cody.ui

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.CommandExecuteParams
import com.sourcegraph.cody.agent.WebviewReceiveMessageStringEncodedParams
import com.sourcegraph.cody.agent.protocol.WebviewCreateWebviewPanelParams
import com.sourcegraph.cody.chat.actions.ExportChatsAction.Companion.gson
import com.sourcegraph.cody.sidebar.WebTheme
import com.sourcegraph.cody.sidebar.WebThemeController
import com.sourcegraph.common.BrowserOpener
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.StandardOpenOption
import kotlin.io.path.Path
import kotlin.math.min
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
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
import java.net.URLDecoder
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import javax.swing.JComponent
import javax.swing.UIManager

// Responsibilities:
// - Creates, tracks all WebUI instances.
// - Pushes theme updates into WebUI instances.
// - Routes messages from host to WebUI instances.
@Service(Service.Level.PROJECT)
class WebUIService(private val project: Project) {
  companion object {
    // TODO: If not disposed, etc.
    @JvmStatic
    fun getInstance(project: Project): WebUIService = project.service<WebUIService>()
  }

  private var proxies: MutableMap<String, WebUIProxy> = mutableMapOf()

  private var themeController =
    WebThemeController().apply { setThemeChangeListener { updateTheme(it) } }

  private fun updateTheme(theme: WebTheme) {
    proxies.values.forEach { it.updateTheme(theme) }
  }

  fun postMessageHostToWebview(handle: String, stringEncodedJsonMessage: String) {
    val proxy = this.proxies[handle] ?: return
    proxy.postMessageHostToWebview(stringEncodedJsonMessage)
  }

  fun createWebviewPanel(params: WebviewCreateWebviewPanelParams) {
    val handle = params.handle
    var view: WebviewViewDelegate? = null
    val delegate = object : WebUIHost {
      override fun postMessageWebviewToHost(stringEncodedJsonMessage: String) {
        CodyAgentService.withAgent(project) {
          it.server.webviewReceiveMessageStringEncoded(
            WebviewReceiveMessageStringEncodedParams(
              handle,
              stringEncodedJsonMessage
            )
          )
        }
      }
      override fun setTitle(value: String) {
        view?.setTitle(value)
      }

      override fun onCommand(command: String) {
        val regex = """^command:([^?]+)(?:\?(.+))?$""".toRegex()
        val matchResult = regex.find(command) ?: return
        val (commandName, encodedArguments) = matchResult.destructured
        val arguments = encodedArguments.takeIf { it.isNotEmpty() }?.let { encoded ->
          val decoded = URLDecoder.decode(encoded, "UTF-8")
          try {
            Gson().fromJson(decoded, JsonArray::class.java).toList()
          } catch (e: Exception) {
            null
          }
        } ?: emptyList()
        println("$arguments")
        if (params.options.enableCommandUris == true || (params.options.enableCommandUris as List<String>).contains(commandName)) {
          CodyAgentService.withAgent(project) {
            it.server.commandExecute(CommandExecuteParams(
              commandName,
              arguments
            ))
          }
        } else {
          println("no")
        }
      }
    }
    val proxy = WebUIProxy.create(delegate)
    proxies[params.handle] = proxy

    // TODO: This should create a panel, but it creates a sidebar view.
    // TODO: Manage tearing down the view when we are done.
    view = CodyViewService.getInstance(project).createView(proxy)

    proxy.updateTheme(themeController.getTheme())
  }

  fun setTitle(handle: String, title: String) {
    val proxy = this.proxies[handle] ?: return
    proxy.title = title
  }

  fun setHtml(handle: String, html: String) {
    val proxy = this.proxies[handle] ?: return
    proxy.html = html
  }
}

val COMMAND_PREFIX = "command:"
// We make up a host name and serve the static resources into the webview apparently from this host.
val PSEUDO_HOST = "file+.sourcegraphstatic.com"
val PSEUDO_ORIGIN = "https://$PSEUDO_HOST"
val PSEUDO_HOST_URL_PREFIX = "$PSEUDO_ORIGIN/"
// TODO, remove this because JB loadHTML uses file URLs.
val MAIN_RESOURCE_URL =
    "${PSEUDO_HOST_URL_PREFIX}main-resource-nonce"

// TODO:
// - Hook up webview/didDispose, etc.

interface WebUIHost {
  fun setTitle(value: String)
  fun postMessageWebviewToHost(stringEncodedJsonMessage: String)
  fun onCommand(command: String)
}

class WebUIProxy(private val host: WebUIHost, private val browser: JBCefBrowserBase) {
  companion object {
    fun create(host: WebUIHost): WebUIProxy {
      val browser =
        JBCefBrowserBuilder()
          .apply {
            setOffScreenRendering(false)
            // TODO: Make this conditional on running in a debug configuration.
            setEnableOpenDevToolsMenuItem(true)
          }
          .build()
      val proxy = WebUIProxy(host, browser)

      val viewToHost =
        JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
          addHandler { query: String ->
            println("webview -> host: $query")
            // TODO: Agent protocol needs a way to inject onDidReceiveMessage events.
            // Thru to AgentWebViewPanel.receiveMessage

            // TODO: Move this query handling to the proxy.
            if (query == "{\"what\":\"DOMContentLoaded\"}") {
              proxy.onDOMContentLoaded()
            }

            val postMessagePrefix = "{\"what\":\"postMessage\",\"value\":"
            if (query.startsWith(postMessagePrefix)) {
              val message = query.substring(postMessagePrefix.length, query.length - "}".length)
              println("host <- webview: $message")
              proxy.postMessageWebviewToHost(message)
            }
            JBCefJSQuery.Response(null)
          }
        }
      // TODO: We could add a second script tag and run when the body element is created.
      val apiScript =
        """
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
                    ${viewToHost.inject("JSON.stringify({what: 'postMessage', value: message})")}
                  },
                  setState: function(newState) {
                      state = newState;
                      // TODO: Route this to wherever VSCode sinks do-update-state.
                      // doPostMessage('do-update-state', JSON.stringify(newState));
                      console.log(`do-update-state: ${'$'}{JSON.stringify(newState)}`);
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

      document.addEventListener('DOMContentLoaded', () => {
        ${viewToHost.inject("JSON.stringify({what:'DOMContentLoaded'})")}
      });
    """
          .trimIndent()
      browser.jbCefClient.addRequestHandler(ExtensionRequestHandler(proxy, apiScript), browser.cefBrowser)
      browser.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandler {
        override fun onBeforePopup(
          browser: CefBrowser,
          frame: CefFrame?,
          targetUrl: String,
          targetFrameName: String?
        ): Boolean {
          if (browser.mainFrame !== frame) {
            BrowserOpener.openInBrowser(null, targetUrl)
            return true
          }
          return false
        }

        override fun onAfterCreated(browser: CefBrowser?) {
        }

        override fun onAfterParentChanged(browser: CefBrowser?) {
        }

        override fun doClose(browser: CefBrowser?): Boolean {
          TODO("Not yet implemented")
        }

        override fun onBeforeClose(browser: CefBrowser?) {
          TODO("Not yet implemented")
        }
      }, browser.cefBrowser)
      // TODO: The extension sets the HTML property, causing this navigation. Move that there.
      // browser.loadURL(MAIN_RESOURCE_URL)
      return proxy
    }
  }

  private var isDOMContentLoaded = false
  private val logger = Logger.getInstance(WebUIProxy::class.java)
  private var theme: WebTheme? = null

  private var _title: String = ""
  var title: String
    get() = _title
    set(value) {
      host.setTitle(value)
      _title = value
    }

  private var _html: String = ""
  var html: String
    get() = _html
    set(value) {
      _html = value
      browser.loadURL("$MAIN_RESOURCE_URL?${value.hashCode()}")
    }

  val component: JComponent? get() = browser.component

  fun onCommand(command: String) {
    host.onCommand(command)
  }

  fun postMessageWebviewToHost(stringEncodedJsonMessage: String) {
    host.postMessageWebviewToHost(stringEncodedJsonMessage)
  }

  fun postMessageHostToWebview(stringEncodedJsonMessage: String) {
    val code =
      """
      (() => {
        let e = new CustomEvent('message');
        e.data = ${stringEncodedJsonMessage};
        window.dispatchEvent(e);
      })()
      """
        .trimIndent()

    // TODO: Consider a better origin that this random made-up origin.
    browser.cefBrowser.mainFrame?.executeJavaScript(code, "cody://postMessage", 0)
  }

  fun onDOMContentLoaded() {
    isDOMContentLoaded = true
    theme?.let { updateTheme(it) }
  }

  fun updateTheme(theme: WebTheme) {
    this.theme = theme
    if (!this.isDOMContentLoaded) {
      logger.info("not updating WebView theme before DOMContentLoaded")
      return
    }
    val code =
        """
    (() => {
      let e = new CustomEvent('message');
      e.data = {
        type: 'ui/theme',
        agentIDE: 'JetBrains',
        cssVariables: ${gson.toJson(theme.variables)},
        isDark: ${theme.isDark}
      };
      window.dispatchEvent(e);
    })()
    """
            .trimIndent()

    browser.cefBrowser.mainFrame?.executeJavaScript(code, "cody://updateTheme", 0)
  }
}

// TODO: Rationalize this with the other Cody view service.
@Service(Service.Level.PROJECT)
class CodyViewService(val project: Project) {
  var toolWindow: ToolWindow? = null

  fun createView(proxy: WebUIProxy): WebviewViewDelegate? {
    // TODO: Handle lazily creating views when the tool window is not available yet.
    val toolWindow = this.toolWindow ?: return null
    toolWindow.isAvailable = true

    // TODO: Design question, do we want to reflect titles at the ToolWindow or at the Content level?

    val lockable = true
    // TODO: Hook up dispose, etc.
    val content = ContentFactory.SERVICE.getInstance()
      .createContent(proxy.component, proxy.title, lockable)
    this.toolWindow?.contentManager?.addContent(content)
    return object : WebviewViewDelegate {
      override fun setTitle(newTitle: String) {
        runInEdt {
          content.displayName = newTitle
        }
      }
      // TODO: Add icon support.
    }
  }

  companion object {
    fun getInstance(project: Project): CodyViewService {
      return project.service()
    }
  }
}

class WebUIToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    // TODO: support this happening AFTER chats are created
    toolWindow.isAvailable = true
    // TODO: Generalize this to support multiple tool windows.
    CodyViewService.getInstance(project).toolWindow = toolWindow
  }
}

interface WebviewViewDelegate {
  fun setTitle(newTitle: String)
}

class ExtensionRequestHandler(private val proxy: WebUIProxy, private val apiScript: String) : CefRequestHandler {
  override fun onBeforeBrowse(
      browser: CefBrowser?,
      frame: CefFrame?,
      request: CefRequest,
      userGesture: Boolean,
      isRedirect: Boolean
  ): Boolean {
    if (request.url.startsWith(COMMAND_PREFIX)) {
      proxy.onCommand(request.url)
      return true
    }
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
      request: CefRequest,
      isNavigation: Boolean,
      isDownload: Boolean,
      requestInitiator: String?,
      disableDefaultHandling: BoolRef?
  ): CefResourceRequestHandler? {
    // JBCef-style loadHTML URLs dump the desired resource URL into a hash in a file:// URL :shrug:
    if (request.url.startsWith(PSEUDO_HOST_URL_PREFIX)) {
      disableDefaultHandling?.set(true)
      return ExtensionResourceRequestHandler(proxy, apiScript)
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

  override fun onRenderProcessTerminated(
      browser: CefBrowser?,
      status: CefRequestHandler.TerminationStatus?
  ) {
    // TODO: Add Telemetry here.
    // TODO: Logging.
    // TODO: Trigger a reload.
  }
}

class ExtensionResourceRequestHandler(private val proxy: WebUIProxy, private val apiScript: String) : CefResourceRequestHandler {
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

  override fun onBeforeResourceLoad(
      browser: CefBrowser?,
      frame: CefFrame?,
      request: CefRequest?
  ): Boolean {
    return false
  }

  override fun getResourceHandler(
      browser: CefBrowser?,
      frame: CefFrame?,
      request: CefRequest
  ): CefResourceHandler {
    return when {
      request.url.startsWith(MAIN_RESOURCE_URL) -> MainResourceHandler(proxy.html.replace(
        "<head>", "<head><script>$apiScript</script>"))
      else -> ExtensionResourceHandler()
    }
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

class ExtensionResourceHandler() : CefResourceHandler {
  private val logger = Logger.getInstance(ExtensionResourceHandler::class.java)
  var status = 0
  var bytesReadFromResource = 0L
  private var bytesSent = 0L
  private var bytesWaitingSend =
      ByteBuffer.allocate(512 * 1024)
          .flip()
  // correctly
  private var contentLength = 0L
  var contentType = "text/plain"
  var readChannel: AsynchronousFileChannel? = null

  override fun processRequest(request: CefRequest, callback: CefCallback?): Boolean {
    val requestPath = URI(request.url).path.removePrefix("/")

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
        logger.warn(
            "Aborting WebView request for ${requestPath}, extension resource directory not found found")
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
      contentType =
          when {
            requestPath.endsWith(".css") -> "text/css"
            requestPath.endsWith(".html") -> "text/html"
            requestPath.endsWith(".js") -> "text/javascript"
            requestPath.endsWith(".png") -> "image/png"
            requestPath.endsWith(".svg") -> "image/svg+xml"
            requestPath.endsWith(".ttf") -> "font/ttf"
            else -> "text/plain"
          }

      // Prepare to read the file contents.
      try {
        readChannel = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)
      } catch (e: IOException) {
        logger.warn(
            "Failed to open file ${file.absolutePath} to serve extension WebView request $requestPath",
            e)
        status = 404
        callback?.Continue()
        return@executeOnPooledThread
      }

      // We're ready to synthesize headers.
      status = 200
      callback?.Continue()
    }
    return true
  }

  override fun getResponseHeaders(
      response: CefResponse?,
      responseLength: IntRef?,
      redirectUrl: StringRef?
  ) {
    response?.status = status
    response?.mimeType = contentType
    // TODO: Security, if we host malicious third-party content would this let them retrieve resources they should not?
    response?.setHeaderByName("access-control-allow-origin", "*", false)
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
      } catch (e: IOException) {}
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
    readChannel?.read(
        bytesWaitingSend,
        bytesReadFromResource,
        null,
        object : CompletionHandler<Int, Void?> {
          override fun completed(result: Int, attachment: Void?) {
            if (result == -1) {
              try {
                readChannel?.close()
              } catch (e: IOException) {}
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
            } catch (e: IOException) {}
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
    } catch (e: IOException) {}
    readChannel = null
  }
}

class MainResourceHandler(content: String) : CefResourceHandler {
  // Copying this all in memory is awful, but Java is awful.
  private val buffer = StandardCharsets.UTF_8.encode(content)

  override fun processRequest(request: CefRequest?, callback: CefCallback?): Boolean {
      callback?.Continue()
      return true
  }

  override fun getResponseHeaders(
    response: CefResponse,
    responseLength: IntRef,
    redirectUrl: StringRef
  ) {
    response.status = 200
    response.mimeType = "text/html"
    responseLength.set(buffer.remaining())
  }

  override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef?, callback: CefCallback?): Boolean {
    if (!buffer.hasRemaining()) {
      return false
    }
    val bytesAvailable = buffer.remaining()
    val bytesToCopy = minOf(bytesAvailable, bytesToRead)
    buffer.get(dataOut, 0, bytesToCopy)
    bytesRead?.set(bytesToCopy)
    return true
  }

  override fun cancel() {
  }
}
