package com.sourcegraph.cody.agent.protocol.util

object Rfc3986UriEncoder {

  // todo solve this with library
  fun encode(uri: String): String {
    val isWindowsPath = uri.matches("^file:///[A-Za-z]:.*".toRegex())
    if (isWindowsPath) {
      val found = "file:///([A-Za-z]):".toRegex().find(uri)!!
      val partition = found.groups[1]!!.value
      return uri.replace("file:///$partition:", "file:///${partition.lowercase()}%3A")
    }
    return uri
  }
}
