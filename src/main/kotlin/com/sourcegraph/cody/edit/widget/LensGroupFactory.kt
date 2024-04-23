package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.diagnostic.Logger
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.edit.sessions.FixupSession
import java.util.*

/** Handles assembling standard groups of lenses. */
class LensGroupFactory(val session: FixupSession) {
  private val logger = Logger.getInstance(LensGroupFactory::class.java)

  fun createTaskWorkingGroup(): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addSpinner(this)
      addSpacer(this)
      addLabel(this, "Cody is working...")
      addSeparator(this)
      addAction(this, "Cancel", FixupSession.COMMAND_CANCEL, FixupSession.ACTION_CANCEL)
      registerWidgets()
    }
  }

  fun createAcceptGroup(): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addLogo(this)
      addSpacer(this)
      addAction(this, "Accept", FixupSession.COMMAND_ACCEPT, FixupSession.ACTION_ACCEPT)
      addSeparator(this)
      addAction(this, "Edit & Retry", FixupSession.COMMAND_RETRY, FixupSession.ACTION_RETRY)
      addSeparator(this)
      addAction(this, "Undo", FixupSession.COMMAND_UNDO, FixupSession.ACTION_UNDO)
      addSeparator(this)
      addAction(this, "Show Diff", FixupSession.COMMAND_DIFF, FixupSession.ACTION_DIFF)
      registerWidgets()
    }
  }

  private fun addSeparator(group: LensWidgetGroup) {
    group.addWidget(LensLabel(group, SEPARATOR))
  }

  private fun addLabel(group: LensWidgetGroup, label: String) {
    group.addWidget(LensLabel(group, label))
  }

  private fun addSpinner(group: LensWidgetGroup) {
    group.addWidget(LensSpinner(group, Icons.StatusBar.CompletionInProgress))
  }

  private fun addLogo(group: LensWidgetGroup) {
    group.addWidget(LensIcon(group, Icons.CodyLogo))
  }

  private fun addSpacer(group: LensWidgetGroup) {
    addLabel(group, ICON_SPACER)
  }

  private fun addAction(group: LensWidgetGroup, label: String, command: String, actionId: String) {
    group.addWidget(LensAction(group, label.uppercase(Locale.getDefault()), command, actionId))

    val hotkey = FixupSession.getHotKey(command)
    if (hotkey.isNotEmpty()) {
      addLabel(group, " ($hotkey)")
    }
  }

  companion object {
    const val ICON_SPACER = " "
    const val SEPARATOR = " | "
  }
}
