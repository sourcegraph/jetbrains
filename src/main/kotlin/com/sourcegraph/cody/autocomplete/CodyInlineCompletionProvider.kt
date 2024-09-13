package com.sourcegraph.cody.autocomplete

import com.intellij.codeInsight.inline.completion.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.project.Project

class CodyInlineCompletionProvider : InlineCompletionProvider {

  override suspend fun getProposals(
      request: InlineCompletionRequest
  ): List<InlineCompletionElement> {
    val editor: Editor = request.editor
    val project: Project? = editor.project

    // Fetch autocompletions using existing logic
    val completions = fetchCompletions(editor, project)

    // Create a list to store InlineCompletionElement objects
    val result = mutableListOf<InlineCompletionElement>()

    // Add completions to the result
    completions.forEach { completion -> result.add(InlineCompletionElement(completion)) }

    return result
  }

  private fun fetchCompletions(editor: Editor, project: Project?): List<String> {
    // Implement your logic to fetch autocompletions
    return listOf("completion1", "completion2", "completion3")
  }

  override fun isEnabled(event: DocumentEvent): Boolean {
    TODO("Not yet implemented")
  }
}
