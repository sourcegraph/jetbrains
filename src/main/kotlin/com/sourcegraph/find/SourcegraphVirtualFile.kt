package com.sourcegraph.find

import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.LocalTimeCounter

class SourcegraphVirtualFile(
    name: String,
    content: CharSequence,
    val repoUrl: String,
    val commit: String?,
    val relativePath: String?
) : LightVirtualFile(name, null, content, LocalTimeCounter.currentTime()) {

  override fun getPath(): String = "$repoUrl > $relativePath"
}
