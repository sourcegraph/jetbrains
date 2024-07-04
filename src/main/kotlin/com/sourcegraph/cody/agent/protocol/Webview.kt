package com.sourcegraph.cody.agent.protocol

data class WebviewCreateWebviewPanelShowOptions(
    val preserveFocus: Boolean,
    val viewColumn: Int,
)

data class WebviewCreateWebviewPanelParams(
  val handle: String,
  val viewType: String,
  val title: String,
  val showOptions: WebviewCreateWebviewPanelShowOptions,
)
