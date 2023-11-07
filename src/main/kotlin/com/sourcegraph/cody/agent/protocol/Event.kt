package com.sourcegraph.cody.agent.protocol

import com.google.gson.JsonObject

data class Event(
    val event: String,
    val anonymousUserId: String,
    val url: String,
    val publicArguments: JsonObject? = null
) {
  val source = "IDEEXTENSION"
  val client = "JETBRAINS_CODY_EXTENSION"
  val referrer = "JETBRAINS"
  // these are used somewhat synonymously
  val userCookieID = anonymousUserId
  val deviceID = anonymousUserId
  val argument = JsonObject()
}
