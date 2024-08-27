package com.sourcegraph.cody.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.CommitMessageParams

class GenerateCommitMessageAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        val filePath = file.path
        val diff = getDiff(filePath) // Implement this method to get the diff of the file
        val template = getTemplate() // Implement this method to get the commit message template

        val params = CommitMessageParams(filePath, diff, template)
        val result = CodyAgentService().generateCommitMessage(params)

        Messages.showMessageDialog(
            project,
            "Commit Message: ${result.commitMessage}\n\nPR Title: ${result.prTitle}\n\nPR Description: ${result.prDescription}",
            "Generated Commit Message",
            Messages.getInformationIcon()
        )
    }

    private fun getDiff(filePath: String): String {
        // Implement the logic to generate the diff for the given file path
        return ""
    }

    private fun getTemplate(): String {
        // Implement the logic to get the commit message template
        return ""
    }
}
