package com.sourcegraph.find.browser

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.intellij.lang.annotations.Language
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function

class JavaToJSBridge(private val browser: JBCefBrowserBase) {
  private val query: JBCefJSQuery = JBCefJSQuery.create(browser)
  private val lock: Lock = ReentrantLock()
  private var handler: Function<String, JBCefJSQuery.Response?>? = null

  /**
   * @param result This is the way to get the result back to the caller from the thread started in
   * this method.
   */
  @JvmOverloads
  fun callJS(action: String, arguments: JsonObject?, result: CompletableFuture<JsonObject?>? = null) {
    // A separate thread is needed because the response handling uses the main thread,
    // so if we did the JS call in the main thread and then waited, the response handler
    // would never be called.
    Thread {
      val logger = Logger.getInstance(this.javaClass)
      // Reason for the locking:
      // JBCefJSQuery objects MUST be created before the browser is loaded, otherwise an
      // error is thrown.
      // As there is only one JBCefJSQuery object, and we need to wait for the result of the
      // last execution,
      // we can only run one query at a time.
      // If this ever becomes a bottleneck, we can create a pool of JBCefJSQuery objects and
      // a counting semaphore.
      lock.lock()

      // This future is needed to communicate between this thread and the response handler
      // thread.
      val handlerCompletedFuture = CompletableFuture<Void?>()
      @Language("javascript")
      val js = """
        window.callJS(
          '$action',
          '${arguments ?: "null"}',
          (result) => {
            ${query.inject("result")}
          }
        );
      """.trimIndent()
      handler =
        Function { responseAsString ->
          query.removeHandler(handler!!)
          handler = null
          try {
            val jsonElement = JsonParser.parseString(responseAsString)
            result?.complete(
              if (jsonElement.isJsonObject) jsonElement.asJsonObject else null
            )
          } catch (e: JsonSyntaxException) {
            logger.warn("Invalid JSON: $responseAsString", e)
            result?.complete(null)
          } finally {
            handlerCompletedFuture.complete(null)
          }
          null
        }
      query.addHandler(handler!!)
      browser.cefBrowser.executeJavaScript(js, browser.cefBrowser.url, 0)
      try {
        handlerCompletedFuture.get()
      } catch (e: InterruptedException) {
        logger.warn("Some problem occurred with the JS response thread.", e)
      } catch (e: ExecutionException) {
        logger.warn("Some problem occurred with the JS response thread.", e)
      } finally {
        // It's only allowed to unlock the lock in the thread where it was locked.
        // This is why `handlerCompletedFuture` is needed in the first place.
        // Otherwise, the handler could simply unlock the lock, but that's not allowed in
        // Java.
        lock.unlock()
      }
    }
      .start()
  }
}
