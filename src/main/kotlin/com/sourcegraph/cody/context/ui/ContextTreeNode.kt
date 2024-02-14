package com.sourcegraph.cody.context.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.CheckedTreeNode

open class ContextTreeNode<T>(value: T, private val onSetChecked: (Boolean) -> Unit) :
    CheckedTreeNode(value) {
  override fun setChecked(checked: Boolean) {
    super.setChecked(checked)
    onSetChecked(checked)
  }
}

class ContextTreeRootNode(
    val text: String,
    isEnabled: Boolean = true,
    onSetChecked: (Boolean) -> Unit = {}
) : ContextTreeNode<String>(text, onSetChecked) {
  init {
    this.isEnabled = isEnabled
  }
}

class ContextTreeRemoteRepoCodebaseNameNode(
    val codebaseName: String,
    onSetChecked: (Boolean) -> Unit
) : ContextTreeNode<String>(codebaseName, onSetChecked)

class ContextTreeLocalRepoNode(val project: Project) : ContextTreeNode<Project>(project, {}) {
  init {
    this.isEnabled = false
  }
}
