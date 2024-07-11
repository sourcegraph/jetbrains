package com.sourcegraph.cody.ui

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel

class WebPanelProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file.fileType == WebPanelFileType.INSTANCE

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return object : FileEditor {
      val component = JLabel("Hello, world")

      override fun <T : Any?> getUserData(key: Key<T>): T? {
        // TODO: Implement this
        return null
      }

      override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        // TODO: Implement this
      }

      override fun dispose() {
        // TODO: Implement this
      }

      override fun getComponent(): JComponent = component

      override fun getPreferredFocusedComponent(): JComponent? = component

      override fun getName(): String = "Cody Web Panel"

      override fun setState(state: FileEditorState) {
        // TODO: Implement this.
      }

      override fun isModified(): Boolean = false

      override fun isValid(): Boolean = true

      override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        // TODO: Do we need to implement this?
      }

      override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        // TODO: Do we need to implement this?
      }

      override fun getCurrentLocation(): FileEditorLocation? = null
    }
  }

  // TODO: Implement dispose, readState, writeState if we need this to manage, restore.
  /*
    override fun disposeEditor(editor: FileEditor) {
      super<FileEditorProvider>.disposeEditor(editor)
    }

    override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
      return super<FileEditorProvider>.readState(sourceElement, project, file)
    }

    override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
      super<FileEditorProvider>.writeState(state, project, targetElement)
    }
  */

  override fun getEditorTypeId(): @NonNls String {
    return "CODY_WEB_PANEL"
  }

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
