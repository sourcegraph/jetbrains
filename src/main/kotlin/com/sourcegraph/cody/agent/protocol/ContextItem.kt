package com.sourcegraph.cody.agent.protocol

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

sealed class ContextItem {
  abstract val type: String
  abstract val uri: URI
  abstract val repoName: String?
  abstract val revision: String?
}

data class ContextItemFile(
    override val uri: URI,
    override val repoName: String?,
    override val revision: String?,
    val isTooLarge: Boolean? = null,
    val range: Range? = null,
) : ContextItem() {
  override val type: String = "file"

  fun isLocal() = repoName == null

  fun getPath(): Path {
    return Paths.get(uri.path).toAbsolutePath()
  }

  fun getLinkActionText(projectPath: String?): String {
    val theRange = if (isLocal()) range?.intellijRange() else range?.toSearchRange()
    val path =
        if (isLocal()) {
          "@${uri.path.removePrefix(projectPath ?: "")}"
        } else {
          val repoCommitFile = uri.path.split("@", "/-/blob/")
          if (repoCommitFile.size == 3) {
            val repo = repoCommitFile[0].split("/").lastOrNull()
            "$repo ${repoCommitFile[2]}"
          } else uri.path
        }

    return buildString {
      append(path)
      if (theRange != null) {
        if (theRange.first < theRange.second) {
          append(":${theRange.first}-${theRange.second}")
        } else {
          append(":${theRange.first}")
        }
      }
    }
  }
}

val contextFileDeserializer: JsonDeserializer<ContextItem> =
    JsonDeserializer { element: JsonElement, _: Type, context: JsonDeserializationContext ->
      when (element.asJsonObject.get("type").asString) {
        "file" -> context.deserialize<ContextItemFile>(element, ContextItemFile::class.java)
        "symbol" -> null
        else -> throw Exception("Unknown discriminator ${element}")
      }
    }

val uriDeserializer =
    JsonDeserializer { jsonElement: JsonElement?, _: Type, _: JsonDeserializationContext ->
      val j = jsonElement?.asJsonObject
      if (j == null || j.isJsonNull) {
        null
      } else if (j.isJsonPrimitive) {
        j.asString
      } else {
        URI(
            j["scheme"]?.asString,
            j["authority"]?.asString,
            j["path"]?.asString,
            j["query"]?.asString,
            j["fragment"]?.asString,
        )
      }
    }

val uriSerializer = JsonSerializer { uri: URI?, _, _ ->
  if (uri == null) {
    JsonNull.INSTANCE
  } else {
    val obj = JsonObject()
    obj.addProperty("scheme", uri.scheme)
    obj.addProperty("authority", uri.authority)
    obj.addProperty("path", uri.path)
    obj.addProperty("query", uri.query)
    obj.addProperty("fragment", uri.fragment)
    obj
  }
}
