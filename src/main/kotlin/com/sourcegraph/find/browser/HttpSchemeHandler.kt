package com.sourcegraph.find.browser

import com.google.common.collect.ImmutableMap
import org.cef.callback.CefCallback
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.min

class HttpSchemeHandler: CefResourceHandlerAdapter() {
  private var data: ByteArray? = null
  private var mimeType: String? = null
  private var responseHeader = 400
  private var offset = 0

  override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
    val extension = getExtension(request.url)
    mimeType = getMimeType(extension) ?: return false
    val url = request.url
    val path = url.replace("http://sourcegraph", "")

    data = loadResource(path)
    responseHeader = if (data != null) 200 else 404
    if (data == null) {
      val defaultContent = getDefaultContent(extension, path)
      data = (defaultContent ?: "").toByteArray()
    }
    callback.Continue()
    return true
  }

  override fun getResponseHeaders(
    response: CefResponse, responseLength: IntRef, redirectUrl: StringRef
  ) {
    response.mimeType = mimeType
    response.status = responseHeader
    responseLength.set(data!!.size)
  }

  override fun readResponse(
    dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback
  ): Boolean {
    var hasData = false
    if (offset < data!!.size) {
      val transferSize = min(bytesToRead.toDouble(), (data!!.size - offset).toDouble()).toInt()
      System.arraycopy(data!!, offset, dataOut, 0, transferSize)
      offset += transferSize
      bytesRead.set(transferSize)
      hasData = true
    } else {
      offset = 0
      bytesRead.set(0)
    }
    return hasData
  }

  private fun loadResource(resourceName: String): ByteArray? {
    try {
      javaClass.getResourceAsStream(resourceName).use { inStream ->
        if (inStream != null) {
          val outFile = ByteArrayOutputStream()
          var readByte: Int
          while ((inStream.read().also { readByte = it }) >= 0) outFile.write(readByte)
          return outFile.toByteArray()
        }
      }
    } catch (_: IOException) {
    }
    return null
  }

  fun getExtension(filename: String?): String? = filename?.takeIf { it.contains(".") }?.substringAfterLast(".")

  fun getDefaultContent(extension: String?, path: String): String? {
    val extensionToDefaultContent: Map<String, String> = ImmutableMap.of(
      "html",
      "<html><head><title>Error 404</title></head>"
              + "<body>"
              + "<h1>Error 404</h1>"
              + "File "
              + path
              + "  does not exist."
              + "</body></html>",
      "js", "",
      "css", ""
    )
    return extensionToDefaultContent[extension]
  }

  fun getMimeType(extension: String?): String? {
    val extensionToMimeType: Map<String?, String> = ImmutableMap.of(
      "html", "text/html",
      "js", "text/javascript",
      "css", "text/css"
    )
    return extensionToMimeType[extension]
  }
}
