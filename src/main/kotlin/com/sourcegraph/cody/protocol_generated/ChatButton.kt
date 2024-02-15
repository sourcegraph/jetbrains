@file:Suppress("FunctionName", "ClassName", "unused")
package com.sourcegraph.cody.protocol_generated

data class ChatButton(
  val label: String? = null,
  val action: String? = null,
  val appearance: AppearanceEnum? = null, // Oneof: primary, secondary, icon
)

enum class AppearanceEnum {
  @com.google.gson.annotations.SerializedName("primary") Primary,
  @com.google.gson.annotations.SerializedName("secondary") Secondary,
  @com.google.gson.annotations.SerializedName("icon") Icon,
}

