package com.sourcegraph.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class RevisionContext(
    val project: Project, // Revision number or commit hash
    val revisionNumber: String,
    val repoRoot: VirtualFile
)
