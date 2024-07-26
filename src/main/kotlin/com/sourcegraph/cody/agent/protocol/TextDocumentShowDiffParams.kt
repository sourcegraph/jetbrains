package com.sourcegraph.cody.agent.protocol

data class TextDocumentShowDiffParams(val uri: String, val edits: List<TextEdit>)
