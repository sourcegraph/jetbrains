@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class ShowWindowMessageParams(
  val severity: SeverityEnum? = null, // Oneof: error, warning, information
  val message: String? = null,
  val options: MessageOptions? = null,
  val items: List<String>? = null,
)

enum class SeverityEnum {
  @com.google.gson.annotations.SerializedName("error") Error,
  @com.google.gson.annotations.SerializedName("warning") Warning,
  @com.google.gson.annotations.SerializedName("information") Information,
}

