package com.sourcegraph.cody.context.ui

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SimpleTextAttributes
import java.io.File
import javax.swing.JTree

class ContextRepositoriesCheckboxRenderer : CheckboxTree.CheckboxTreeCellRenderer() {

  override fun customizeRenderer(
      tree: JTree?,
      node: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
  ) {
    val style =
        if (ApplicationInfo.getInstance().build.baselineVersion > 233) "style='color:#808080'"
        else ""

    when (node) {
      is ContextTreeRemoteRepoNode -> {
        val repoName =
            node.codebaseName.value.split(File.separator).lastOrNull() ?: node.codebaseName.value
        textRenderer.appendHTML(
            "<b>${repoName}</b> <span ${style}>${node.codebaseName.value}</span>",
            SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
      is ContextTreeLocalRepoNode -> {
        val projectPath = node.project.basePath?.replace(System.getProperty("user.home"), "~")
        textRenderer.appendHTML(
            "<b>${node.project.name}</b> <i ${style}>${projectPath}</i>",
            SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
      is CheckedTreeNode -> {
        textRenderer.appendHTML(
            "<b>${node.userObject}</b>", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }

      // The root node:
      // - Label: "Chat Context"
      // - Summary string: Multi repo, 9 Repos
      // - myCheckbox.setVisible(false)

      // The intermediate node:
      // - Label: 9 repos on
      // - Summary string: customer.sourcegraph.com

      // The repo nodes:
      // - getTextRenderer().setIcon(...) <-- the icon for the repo name
    }
  }
}
