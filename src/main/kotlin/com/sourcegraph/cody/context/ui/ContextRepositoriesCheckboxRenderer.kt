package com.sourcegraph.cody.context.ui

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.ThreeStateCheckBox
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
      // Consumer context node renderers
      is ContextTreeLocalRepoNode -> {
        val projectPath = node.project.basePath?.replace(System.getProperty("user.home"), "~")
        textRenderer.appendHTML(
            "<b>${node.project.name}</b> <i ${style}>${projectPath}</i>",
            SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }

      // Enterprise context node renderers

      is ContextTreeEnterpriseRootNode -> {
        textRenderer.appendHTML(
          "<b>Chat Context</b> <span ${style}>${node.numRepos} Repos on ${node.endpointName}</span>",
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        )
        // TODO: Remove, now the path has identity
        // myCheckbox.state = if (node.isChecked) { ThreeStateCheckBox.State.SELECTED } else { ThreeStateCheckBox.State.NOT_SELECTED }
      }
      is ContextTreeRemotesNode -> {
        textRenderer.append(
          "Repos",
          SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        )
        myCheckbox.isVisible = false
      }
      is ContextTreeRemoteRepoNode -> {
        textRenderer.appendHTML(
          "<b>${node.repo.displayName}</b>",
          SimpleTextAttributes.REGULAR_ATTRIBUTES)
        textRenderer.icon = node.repo.icon
      }

      // Fallback
      is CheckedTreeNode -> {
        textRenderer.appendHTML(
            "<b>${node.userObject}</b>", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
  }
}
