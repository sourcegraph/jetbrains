@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class EventProperties(
  val anonymousUserID: String? = null,
  val prefix: String? = null,
  val client: String? = null,
  val source: SourceEnum? = null, // Oneof: IDEEXTENSION
)

enum class SourceEnum {
  @com.google.gson.annotations.SerializedName("IDEEXTENSION") IDEEXTENSION,
}

