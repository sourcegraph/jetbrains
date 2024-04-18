package com.sourcegraph.cody.internals

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory

class InternalsStatusBarWidgetFactory : StatusBarEditorBasedWidgetFactory() {
  override fun getId(): String = ID

  override fun getDisplayName(): String = "⚠\uFE0F Cody Internals"

  override fun createWidget(project: Project): StatusBarWidget = InternalsStatusBarWidget(project)

  override fun disposeWidget(widget: StatusBarWidget) {
    Disposer.dispose(widget)
  }

  companion object {
    const val ID = "cody.internalsStatusBarWidget"
  }
}
