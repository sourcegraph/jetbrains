package com.sourcegraph.cody.agent.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.net.URI
import kotlin.io.path.toPath

class ContextItemTest : BasePlatformTestCase() {

  fun `test getPath`() {
    fun contextFilePath(path: String) = VfsUtil.toUri(path)?.toPath().toString()

    if (SystemInfoRt.isWindows) {
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("file:///c:/a/b/c/d.java"))
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("file://c:/a/b/c/d.java"))
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("file:///c:/a/b/c/d.java?#"))
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("file://c:/a/b/c/d.java?#"))
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("/c:/a/b/c/d.java"))
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("c:/a/b/c/d.java"))
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("c:/a/b/c/d.java?#"))
      assertEquals("c:\\a\\b\\c\\d.java", contextFilePath("/c:/a/b/c/d.java?#"))
    } else {
      assertEquals("/a/b/c/d.java", contextFilePath("/a/b/c/d.java"))
      assertEquals("/a/b/c/d.java", contextFilePath("/a/b/c/d.java?#"))
      assertEquals("/a/b/c/d.java", contextFilePath("file:///a/b/c/d.java"))
    }
  }

  fun `test uri serialization and deserialization`() {
    val gson: Gson =
        GsonBuilder()
            .registerTypeAdapter(URI::class.java, uriDeserializer)
            .registerTypeAdapter(URI::class.java, uriSerializer)
            .serializeNulls()
            .create()

    fun roundtripConversion(path: String) =
        gson.fromJson(gson.toJson(URI.create(path)), URI::class.java).toString()

    if (SystemInfoRt.isWindows) {
      assertEquals("file:/c:/a/b/c/d.java", roundtripConversion("file:///c:/a/b/c/d.java"))
      assertEquals("file://c:/a/b/c/d.java", roundtripConversion("file://c:/a/b/c/d.java"))
      assertEquals("file:/c:/a/b/c/d.java?#", roundtripConversion("file:///c:/a/b/c/d.java?#"))
      assertEquals("file://c:/a/b/c/d.java?#", roundtripConversion("file://c:/a/b/c/d.java?#"))
      assertEquals("/c:/a/b/c/d.java", roundtripConversion("/c:/a/b/c/d.java"))
      assertEquals("c:/a/b/c/d.java", roundtripConversion("c:/a/b/c/d.java"))
      assertEquals("c:/a/b/c/d.java?#", roundtripConversion("c:/a/b/c/d.java?#"))
      assertEquals("/c:/a/b/c/d.java?#", roundtripConversion("/c:/a/b/c/d.java?#"))
    } else {
      assertEquals("/a/b/c/d.java", roundtripConversion("/a/b/c/d.java"))
      assertEquals("/a/b/c/d.java?#", roundtripConversion("/a/b/c/d.java?#"))
      assertEquals("file:/a/b/c/d.java", roundtripConversion("file:///a/b/c/d.java"))
    }
  }
}
