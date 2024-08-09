package com.sourcegraph.cody.chat

import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.sourcegraph.cody.chat.ui.ContextFileActionLink
import com.sourcegraph.cody.context.ui.EnterpriseEnhancedContextPanel
import com.sourcegraph.cody.history.HistoryService
import com.sourcegraph.cody.history.state.EnhancedContextState
import com.sourcegraph.cody.history.state.RemoteRepositoryState
import com.sourcegraph.cody.util.CodyIntegrationTextFixture
import com.sourcegraph.cody.util.CustomJunitClassRunner
import com.sourcegraph.cody.util.TestingCredentials
import java.awt.Component
import java.awt.Container
import java.util.concurrent.TimeUnit
import junit.framework.TestCase
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(CustomJunitClassRunner::class)
class ChatTest : CodyIntegrationTextFixture() {
  override fun myCredentials() = TestingCredentials.enterprise

  @Test
  fun testRemoteContextFileItems() {
    val enhancedContextState =
        EnhancedContextState().apply {
          remoteRepositories.add(
              RemoteRepositoryState().apply {
                isEnabled = true
                remoteUrl = "github.com/sourcegraph/cody"
                codebaseName = "github.com/sourcegraph/cody"
              })
        }
    HistoryService.getInstance(project).updateDefaultContextState(enhancedContextState)

    val session = runInEdtAndGet { AgentChatSession.createNew(project) }

    await.atMost(5, TimeUnit.SECONDS) until
        {
          (session.getPanel().contextView as EnterpriseEnhancedContextPanel)
              .controller
              .getConfiguredState()
              .find { it.name == "github.com/sourcegraph/cody" } != null
        }

    runInEdtAndWait { session.sendMessage("How do we use JSON RPC in Cody?", emptyList()) }

    await.atMost(5, TimeUnit.SECONDS) until
        {
          val messages = session.messages
          messages.size == 2 && !messages[0].contextFiles.isNullOrEmpty()
        }

    val linkPanels =
        findComponentsRecursively(session.getPanel(), ContextFileActionLink::class.java)

    TestCase.assertEquals(
        listOf(
            "cody agent/CHANGELOG.md",
            "cody agent/README.md",
            "cody agent/src/__tests__/chat-response-quality/README.md",
            "cody agent/src/cli/command-jsonrpc-stdio.ts",
            "cody agent/src/cli/command-jsonrpc-websocket.ts",
            "cody agent/src/cli/command-root.ts",
            "cody agent/src/cli/scip-codegen/JvmCodegen.ts",
            "cody agent/src/cli/scip-codegen/JvmFormatter.ts",
            "cody agent/src/jsonrpc-alias.ts",
            "cody lib/icons/README.md",
            "cody vscode/CONTRIBUTING.md",
            "cody vscode/src/graph/bfg/spawn-bfg.ts",
            "cody vscode/src/jsonrpc/bfg-protocol.ts",
            "cody vscode/src/jsonrpc/CodyJsonRpcErrorCode.ts",
            "cody vscode/src/jsonrpc/context-ranking-protocol.ts",
            "cody vscode/src/jsonrpc/isRunningInsideAgent.ts",
            "cody vscode/src/jsonrpc/jsonrpc.ts",
            "cody vscode/src/jsonrpc/TextDocumentWithUri.test.ts",
            "cody vscode/src/jsonrpc/TextDocumentWithUri.ts",
            "cody web/lib/agent/agent.client.ts"),
        linkPanels.map { panel -> panel.text })
  }

  private fun <A> findComponentsRecursively(parent: Component, targetClass: Class<A>): List<A> {
    val result = mutableListOf<A>()

    if (targetClass.isInstance(parent)) {
      result.add(parent as A)
    }

    if (parent is Container) {
      for (component in parent.components) {
        result.addAll(findComponentsRecursively(component, targetClass))
      }
    }

    return result
  }
}
