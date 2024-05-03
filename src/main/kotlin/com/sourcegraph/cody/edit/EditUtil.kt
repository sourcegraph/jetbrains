package com.sourcegraph.cody.edit

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.ignore.IgnoreOracle
import com.sourcegraph.cody.ignore.IgnorePolicy
import com.sourcegraph.config.ThemeUtil
import java.awt.Color
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager

object EditUtil {
  private val logger = Logger.getInstance(EditUtil::class.java)

  // Puts `name` as a client property on a component.
  // This can be very useful for debugging mouse, keyboard and focus issues,
  // which are commonplace in Swing. You can see the client property "name"
  // in the debugger, to distinguish otherwise similar-looking components.
  private fun <T : JComponent> setComponentName(component: T, name: String): T {
    component.putClientProperty("name", name)
    return component
  }

  fun namedButton(name: String): JButton = setComponentName(JButton(), name)

  fun namedLabel(name: String): JLabel = setComponentName(JLabel(), name)

  fun namedPanel(name: String): JPanel = setComponentName(JPanel(), name)

  fun removeAllListeners(component: JComponent) {
    // Remove Mouse Listeners
    component.mouseListeners.forEach { component.removeMouseListener(it) }

    // Remove Mouse Motion Listeners
    component.mouseMotionListeners.forEach { component.removeMouseMotionListener(it) }

    // Remove Action Listeners if the component is a type of AbstractButton
    (component as? AbstractButton)?.actionListeners?.forEach { component.removeActionListener(it) }

    // Remove Key Listeners
    component.keyListeners.forEach { component.removeKeyListener(it) }

    // Recursively remove listeners from child components
    component.components.forEach {
      if (it is JComponent) {
        removeAllListeners(it)
      }
    }
  }

  // Get a theme color like "Button.default.borderColor".
  fun getThemeColor(key: String): Color? {
    return UIManager.getColor(key)
  }

  // Dark -> darker. Bright -> brighter.
  fun getEnhancedThemeColor(key: String): Color? {
    return enhance(getThemeColor(key) ?: return null)
  }

  fun getSubduedThemeColor(key: String): Color? {
    return subdue(getThemeColor(key) ?: return null)
  }

  fun getMutedThemeColor(key: String): Color? {
    return mute(getThemeColor(key) ?: return null)
  }

  // Makes the color more prominent.
  fun enhance(color: Color): Color {
    return if (ThemeUtil.isDarkTheme()) {
      color.darker()
    } else {
      color.brighter()
    }
  }

  // Makes the color less prominent.
  fun subdue(color: Color): Color {
    return if (ThemeUtil.isDarkTheme()) {
      color.brighter()
    } else {
      color.darker()
    }
  }

  // Makes the color strictly darker.
  fun mute(color: Color): Color {
    return if (ThemeUtil.isDarkTheme()) {
      color
    } else {
      color.darker()
    }
  }

  /**
   * Return true if the currently selected text editor for `project` is visiting a file that is
   * currently ignored by CodyIgnore.
   */
  fun currentlySelectedFileIsIgnored(project: Project?): Boolean {
    if (project == null || project.isDisposed) {
      return false
    }
    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    if (editor == null || editor.isDisposed) {
      return false
    }
    val virtualFileUrl =
        FileDocumentManager.getInstance().getFile(editor.document)?.url ?: return false
    return try {
      val policy =
          IgnoreOracle.getInstance(project).policyForUri(virtualFileUrl).get(30, TimeUnit.SECONDS)
      policy == IgnorePolicy.IGNORE
    } catch (x: TimeoutException) {
      logger.warn("Timed out getting ignore policy for $virtualFileUrl", x)
      false
    }
  }
}
