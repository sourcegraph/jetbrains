/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

import com.google.gson.annotations.SerializedName;

data class WebviewNativeConfig(
  val view: ViewEnum, // Oneof: multiple, single
  val cspSource: String? = null,
  val webviewBundleServingPrefix: String? = null,
  val skipResourceRelativization: Boolean? = null,
  val injectScript: String? = null,
  val injectStyle: String? = null,
) {

  enum class ViewEnum {
    @SerializedName("multiple") Multiple,
    @SerializedName("single") Single,
  }
}

