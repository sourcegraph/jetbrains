package com.sourcegraph.cody.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

interface CodyPsiRangeProvider {
    fun getDocumentableRange(project: Project, editor: Editor): DocumentableRange?
}

data class DocumentableRange(val startOffset: Int, val endOffset: Int)
