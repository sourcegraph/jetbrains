package com.sourcegraph.cody.context.ui

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.ThreeStateCheckBox
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.chat.ui.pluralize
import com.sourcegraph.cody.context.RepoInclusion
import com.sourcegraph.common.CodyBundle
import com.sourcegraph.common.CodyBundle.fmt
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JTree

class ContextRepositoriesCheckboxRenderer(private val enhancedContextEnabled: AtomicBoolean) :
    CheckboxTree.CheckboxTreeCellRenderer() {

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

      is ContextTreeEditReposNode -> {
        myCheckbox.isVisible = false
        textRenderer.appendHTML(
          CodyBundle.getString(when {
            node.hasRemovableRepos -> "context-panel.tree.node-edit-repos.label-edit"
            else -> "context-panel.tree.node-edit-repos.label-add"
          })
            .fmt(style),
          SimpleTextAttributes.REGULAR_ATTRIBUTES
        )
        textRenderer.icon = when {
          node.hasRemovableRepos -> Icons.Actions.Edit
          else -> Icons.Actions.Add
        }
      }

      is ContextTreeEnterpriseRootNode -> {
        // Compute a complicated label counting repositories, for example:
        // *Chat Context* 1 Repo on example.com
        // *Chat Context* 2 Repos - 1 ignored on example.com
        val ignoredRejoinder =
            when {
              node.numIgnoredRepos > 0 ->
                  CodyBundle.getString("context-panel.tree.node-chat-context.detail-ignored-repos")
                      .fmt(node.numIgnoredRepos.toString())
              else -> ""
            }
        textRenderer.appendHTML(
            CodyBundle.getString("context-panel.tree.node-chat-context.detailed")
                .fmt(
                    style,
                    node.numRepos.toString(),
                    "Repo".pluralize(node.numRepos),
                    ignoredRejoinder,
                    node.endpointName),
            SimpleTextAttributes.REGULAR_ATTRIBUTES)
        // The root element controls enhanced context which includes editor selection, etc. Do not
        // display unchecked/bar even if the child repos are unchecked.
        myCheckbox.state =
            if (node.isChecked) {
              ThreeStateCheckBox.State.SELECTED
            } else {
              ThreeStateCheckBox.State.NOT_SELECTED
            }
        toolTipText = ""
        myCheckbox.toolTipText = ""
      }
      is ContextTreeRemoteRepoNode -> {
        val isEnhancedContextEnabled = enhancedContextEnabled.get()

        textRenderer.appendHTML(
          CodyBundle.getString("context-panel.tree.node-remote-repo.label").fmt(style, node.repo.name, when {
            node.repo.inclusion == RepoInclusion.AUTO -> CodyBundle.getString("context-panel.tree.node-remote-repo.auto")
            else -> ""
          }), SimpleTextAttributes.REGULAR_ATTRIBUTES)

        textRenderer.icon = node.repo.icon

        toolTipText =
            when {
              node.repo.isIgnored == true ->
                  CodyBundle.getString("context-panel.tree.node-ignored.tooltip")
              node.repo.inclusion == RepoInclusion.AUTO ->
                  CodyBundle.getString("context-panel.tree.node-auto.tooltip")
              else -> node.repo.name
            }
        myCheckbox.state =
            when {
              isEnhancedContextEnabled &&
                  node.repo.isEnabled == true &&
                  node.repo.isIgnored != true -> ThreeStateCheckBox.State.SELECTED
              node.repo.isEnabled == true -> ThreeStateCheckBox.State.DONT_CARE
              else -> ThreeStateCheckBox.State.NOT_SELECTED
            }
        myCheckbox.isEnabled = isEnhancedContextEnabled && node.repo.inclusion != RepoInclusion.AUTO
        myCheckbox.toolTipText =
            when {
              node.repo.inclusion == RepoInclusion.AUTO ->
                  CodyBundle.getString("context-panel.tree.node-auto.tooltip")
              else -> CodyBundle.getString("context-panel.tree.node.checkbox.remove-tooltip")
            }
      }

      // Fallback
      is CheckedTreeNode -> {
        textRenderer.appendHTML(
            "<b>${node.userObject}</b>", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      }
    }
  }
}
