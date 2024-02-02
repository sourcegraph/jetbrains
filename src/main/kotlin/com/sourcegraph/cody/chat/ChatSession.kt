package com.sourcegraph.cody.chat

import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.ExtensionMessage
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.*
import com.sourcegraph.cody.vscode.CancellationToken

typealias SessionId = String

interface ChatSession {

  fun sendWebviewMessage(message: WebviewMessage)

  @RequiresEdt fun sendMessage(text: String, contextFiles: List<ContextFile>)

  fun receiveMessage(extensionMessage: ExtensionMessage)

  fun getCancellationToken(): CancellationToken

  fun getInternalId(): String
}
