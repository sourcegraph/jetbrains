package com.sourcegraph.cody.ignore

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBEmptyBorder
import com.sourcegraph.Icons
import com.sourcegraph.common.CodyBundle
import java.awt.Dimension

class CommandPanelIgnoreBanner(val project: Project) : NonOpaquePanel(), IgnoreOracle.FocusedFileIgnorePolicyListener {
  init {
    ApplicationManager.getApplication().assertIsDispatchThread()
  }

  private val banner = EditorNotificationPanel().apply {
    text = CodyBundle.getString("ignore.sidebar-panel-ignored-file.text")
    createActionLabel(
      CodyBundle.getString("ignore.sidebar-panel-ignored-file.learn-more-cta"),
      { BrowserUtil.browse(CODY_IGNORE_DOCS_URL) }, false)
    icon(Icons.CodyLogoSlash)
  }

  init {
    addHierarchyListener {
      if (!project.isDisposed) {
        if (it.component.isShowing) {
          IgnoreOracle.getInstance(project).addListener(this)
        } else {
          IgnoreOracle.getInstance(project).removeListener(this)
        }
      }
    }
  }

  private fun update(policy: IgnorePolicy) {
     when (policy) {
      IgnorePolicy.USE -> {
        remove(banner)
        border = JBEmptyBorder(0)
      }
      IgnorePolicy.IGNORE -> {
        add(banner)

        // These colors cribbed from EditorComposite, createTopBottomSideBorder
        val scheme = EditorColorsManager.getInstance().globalScheme
        val borderColor =
          scheme.getColor(EditorColors.SEPARATOR_ABOVE_COLOR) ?: scheme.getColor(EditorColors.TEARLINE_COLOR)
        border = SideBorder(borderColor, SideBorder.TOP or SideBorder.BOTTOM)
      }
    }
  }

  override fun getMaximumSize(): Dimension {
    val size = super.getMaximumSize()
    size.height = preferredSize.height
    return size
  }

  override fun focusedFileIgnorePolicyChanged(policy: IgnorePolicy) {
    runInEdt {
      update(policy)
    }
  }
}