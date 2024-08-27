package com.sourcegraph.cody.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.CommitMessageParams
import com.sourcegraph.cody.agent.protocol.CommitMessageResult
import org.junit.Test
import org.mockito.Mockito

class GenerateCommitMessageActionTest : BasePlatformTestCase() {

    @Test
    fun testActionPerformed() {
        val project = project
        val file = myFixture.configureByText("TestFile.java", "class Test {}").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!

        val editor = EditorFactory.getInstance().createEditor(document, project)
        val event = Mockito.mock(AnActionEvent::class.java)
        Mockito.`when`(event.project).thenReturn(project)
        Mockito.`when`(event.getData(CommonDataKeys.EDITOR)).thenReturn(editor)

        val diff = "diff --git a/TestFile.java b/TestFile.java\nindex 0000000..e69de29"
        val template = "feat: "

        val params = CommitMessageParams(file.path, diff, template)
        val result = CommitMessageResult("feat: add TestFile", "Add TestFile", "This PR adds TestFile.java")

        val service = Mockito.mock(CodyAgentService::class.java)
        Mockito.`when`(service.generateCommitMessage(params)).thenReturn(result)

        WriteCommandAction.runWriteCommandAction(project) {
            GenerateCommitMessageAction().actionPerformed(event)
        }

        val dialog = Messages.getInstance().getDialog("Generated Commit Message")
        assertNotNull(dialog)
        assertTrue(dialog.message.contains("feat: add TestFile"))
        assertTrue(dialog.message.contains("Add TestFile"))
        assertTrue(dialog.message.contains("This PR adds TestFile.java"))

        EditorFactory.getInstance().releaseEditor(editor)
    }
}
