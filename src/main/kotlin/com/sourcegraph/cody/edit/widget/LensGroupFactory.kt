package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.diagnostic.Logger
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.edit.EditCommandPrompt
import com.sourcegraph.cody.edit.sessions.FixupSession
import javax.swing.Icon

/** Handles assembling standard groups of lenses. */
class LensGroupFactory(val session: FixupSession) {
  private val logger = Logger.getInstance(LensGroupFactory::class.java)

  fun createTaskWorkingGroup(): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addLogo(this)
      addSpinner(this)
      addLabel(this, "Generating Code Edits")
      addSeparator(this)
      addAction(this, "Cancel", FixupSession.ACTION_CANCEL)
      registerWidgets()
    }
  }

  fun createAcceptGroup(): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addLogo(this)
      addAction(this, "Accept", FixupSession.ACTION_ACCEPT)
      addSeparator(this)
      addAction(this, "Undo", FixupSession.ACTION_UNDO)
      addSeparator(this)
      addAction(this, "Edit & Retry", FixupSession.ACTION_RETRY)
      addSeparator(this)
      addAction(this, "Show Diff", FixupSession.ACTION_DIFF)
      registerWidgets()
      isAcceptGroup = true
    }
  }

  fun createErrorGroup(
      message: String, // The message to show in the error lens; should be short.
      tooltip: String? = null  // Can show more detail here.
  ): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addLogo(this)
      addErrorIcon(this)
      if (tooltip != null) {
        addLabel(this, message).apply { hoverText = tooltip }
      }
      addSeparator(this)
      addAction(this, "Dismiss", FixupSession.ACTION_DISMISS)
      addSeparator(this)
      addAction(this, "Open Log", "cody.openLogAction")
      isErrorGroup = true
    }
  }

  private fun addSeparator(group: LensWidgetGroup) {
    group.addWidget(LensLabel(group, SEPARATOR))
  }

  private fun addLabel(
      group: LensWidgetGroup,
      label: String,
  ): LensLabel {
    return LensLabel(group, label).apply { group.addWidget(this) }
  }

  private fun addSpinner(group: LensWidgetGroup) {
    group.addWidget(LensSpinner(group, Icons.StatusBar.CompletionInProgress))
    addSpacer(group)
  }

  private fun addLogo(group: LensWidgetGroup) {
    addIcon(group, Icons.StatusBar.CodyAvailable)
  }

  private fun addSpacer(group: LensWidgetGroup) {
    addLabel(group, ICON_SPACER)
  }

  private fun addAction(group: LensWidgetGroup, label: String, actionId: String) {
    group.addWidget(LensAction(group, label, actionId))

    val hotkey = EditCommandPrompt.getShortcutText(actionId)
    if (!hotkey.isNullOrEmpty()) {
      group.addWidget(LensHotkey(group, hotkey))
    }
  }

  private fun addErrorIcon(group: LensWidgetGroup) {
    addIcon(group, Icons.Edit.Error)
  }

  private fun addIcon(group: LensWidgetGroup, icon: Icon) {
    group.addWidget(LensIcon(group, icon))
    addSpacer(group)
  }

  companion object {
    const val ICON_SPACER = " "
    const val SEPARATOR = " | "
  }
}
