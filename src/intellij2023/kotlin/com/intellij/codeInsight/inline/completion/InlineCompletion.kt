package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

object InlineCompletion {
  private val KEY = Key.create<InlineCompletionHandler>("inline.completion.handler")

  fun getHandlerOrNull(editor: Editor): InlineCompletionHandler? = editor.getUserData(KEY)
}
