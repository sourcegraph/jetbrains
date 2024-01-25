package com.sourcegraph.cody.context.ui

import com.intellij.openapi.actionSystem.ActionToolbarPosition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ToolbarDecorator
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.common.CodyBundle
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.JPanel

class EnhancedContextPanel(private val project: Project) : JPanel() {

  val isEnhancedContextEnabled = AtomicBoolean(true)

  private val reindexButton = ReindexButton(project)

  private val helpButton = HelpButton()

  private val tree = run {
    val treeRoot = CheckedTreeNode(CodyBundle.getString("context-panel.tree-root"))
    val chatContext = CheckedTreeNode(CodyBundle.getString("context-panel.tree-chat-context"))
    val currentRepo =
        object : CheckedTreeNode(project) {
          override fun isChecked(): Boolean = isEnhancedContextEnabled.get()

          override fun setChecked(checked: Boolean) {
            isEnhancedContextEnabled.set(checked)
            CodyAgentService.getInstance(project).restartAgent(project)
          }
        }
    chatContext.add(currentRepo)
    treeRoot.add(chatContext)
    CheckboxTree(ContextRepositoriesCheckboxRenderer(), treeRoot)
  }

  private val toolbarPanel = run {
    //    val borderColor = ColorUtil.brighter(UIUtil.getPanelBackground(), 3)
    //    val lightBorder = BorderFactory.createMatteBorder(1, 0, 0, 1, borderColor)
    //    val titledBorder = TitledBorder(lightBorder,
    // CodyBundle.getString("context-panel.panel-name"))

    ToolbarDecorator.createDecorator(tree)
        .disableUpDownActions()
        .addExtraAction(reindexButton)
        .addExtraAction(helpButton)
        .setPreferredSize(Dimension(0, 30))
        .setToolbarPosition(ActionToolbarPosition.LEFT)
        //        .setPanelBorder(titledBorder)
        .setScrollPaneBorder(BorderFactory.createEmptyBorder())
        .setToolbarBorder(BorderFactory.createEmptyBorder())
        .createPanel()
  }

  init {
    layout = VerticalFlowLayout(VerticalFlowLayout.BOTTOM, 0, 5, true, false)

    // Fix for https://github.com/sourcegraph/jetbrains/issues/344
    // Sometimes we are not instantiated on the EDT.
    ApplicationManager.getApplication().invokeLater {
      tree.expandRow(0)
      add(toolbarPanel)
    }
  }
}
