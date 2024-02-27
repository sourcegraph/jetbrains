package com.sourcegraph.cody.context.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.DumbAwareActionButton
import javax.swing.Icon

open class ContextToolbarButton(
    name: String,
    icon: Icon,
    private val buttonAction: () -> Unit = {}
) : DumbAwareActionButton(name, icon) {

  @Suppress("MissingRecentApi")
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isDumbAware(): Boolean = true

  override fun actionPerformed(p0: AnActionEvent) {
    buttonAction()
  }
}
