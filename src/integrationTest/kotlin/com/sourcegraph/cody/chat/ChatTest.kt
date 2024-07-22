package com.sourcegraph.cody.chat

import com.intellij.testFramework.runInEdtAndGet
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.WebviewMessage
import com.sourcegraph.cody.agent.protocol.GetRepoIdsParam
import com.sourcegraph.cody.agent.protocol.Repo
import com.sourcegraph.cody.chat.ui.ContextFileActionLink
import com.sourcegraph.cody.util.CodyIntegrationTextFixture
import com.sourcegraph.cody.util.CustomJunitClassRunner
import com.sourcegraph.cody.util.TestingCredentials
import java.awt.Component
import java.awt.Container
import java.util.concurrent.CompletableFuture
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

    val result = CompletableFuture<List<Repo>>()
    CodyAgentService.withAgent(project) { agent ->
      try {
        val codebaseNames = listOf("github.com/sourcegraph/cody")
        val param = GetRepoIdsParam(codebaseNames, codebaseNames.size)
        val repos = agent.server.getRepoIds(param).get()
        result.complete(repos?.repos ?: emptyList())
      } catch (e: Exception) {
        result.complete(emptyList())
      }
    }

    await.atMost(5, TimeUnit.SECONDS) until { result.isDone }

    val repos = result.get()
    TestCase.assertEquals(1, repos.size)

    val session = runInEdtAndGet {
      val mySession = AgentChatSession.createNew(project)
      mySession.sendWebviewMessage(
          WebviewMessage(command = "context/choose-remote-search-repo", explicitRepos = repos))
      mySession.sendMessage("what is json rpc", emptyList())
      mySession
    }

    await.atMost(10, TimeUnit.SECONDS) until
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
