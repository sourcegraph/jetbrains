package com.sourcegraph.cody.agent.protocol_helpers

import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities as ProtocolClientCapabilities
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.ChatEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.CodeLensesEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.CompletionsEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.EditEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.EditWorkspaceEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.GitEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.IgnoreEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.ProgressBarsEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.ShowDocumentEnum
import com.sourcegraph.cody.agent.protocol_generated.ClientCapabilities.UntitledDocumentsEnum

data class ClientCapabilities(
    var completions: String? = null,
    var chat: String? = null,
    var git: String? = null,
    var progressBars: String? = null,
    var edit: String? = null,
    var editWorkspace: String? = null,
    var codeLenses: String? = null,
    val showDocument: String? = null,
    val ignore: String? = null,
    val untitledDocuments: String? = null
)

// TODO: Can't we set default capabilities here?
fun ClientCapabilities.toProtocol(): ProtocolClientCapabilities {
  return ProtocolClientCapabilities(
      completions = this.completions?.toEnumIgnoreCase<CompletionsEnum>(),
      chat = this.chat?.toEnumIgnoreCase<ChatEnum>(),
      git = this.git?.toEnumIgnoreCase<GitEnum>(),
      progressBars = this.progressBars?.toEnumIgnoreCase<ProgressBarsEnum>(),
      edit = this.edit?.toEnumIgnoreCase<EditEnum>(),
      editWorkspace = this.editWorkspace?.toEnumIgnoreCase<EditWorkspaceEnum>(),
      codeLenses = this.codeLenses?.toEnumIgnoreCase<CodeLensesEnum>(),
      showDocument = this.showDocument?.toEnumIgnoreCase<ShowDocumentEnum>(),
      ignore = this.ignore?.toEnumIgnoreCase<IgnoreEnum>(),
      untitledDocuments = this.untitledDocuments?.toEnumIgnoreCase<UntitledDocumentsEnum>())
}
