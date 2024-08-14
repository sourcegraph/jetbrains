package com.sourcegraph.cody.edit.widget

import com.sourcegraph.cody.CodyToolWindowContent.Companion.logger
import com.sourcegraph.cody.Icons
import com.sourcegraph.cody.agent.protocol.EditTask
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.edit.EditCommandPrompt
import com.sourcegraph.cody.edit.sessions.FixupSession
import javax.swing.Icon

/** Handles assembling standard groups of lenses. */
class LensGroupFactory(val session: FixupSession) {

  fun createTaskWorkingGroup(): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addLogo(this)
      addSpinner(this)
      addLabel(this, "Generating Code Edits")
      addSeparator(this)
      addAction(this, "Cancel", FixupSession.ACTION_CANCEL)
      registerWidgets()
      isInWorkingGroup = true
    }
  }

  fun createHeaderGroup(isUnitTestCommand: Boolean = false): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addLogo(this)
      addAction(this, "Accept All", FixupSession.ACTION_ACCEPT_ALL)
      addSeparator(this)
      addAction(this, "Reject All", FixupSession.ACTION_REJECT_ALL)
      addSeparator(this)
      // Exclude Edit & Retry from unit test commands
      if (!isUnitTestCommand) {
        addAction(this, "Edit & Retry", FixupSession.ACTION_RETRY)
        addSeparator(this)
      }
      addAction(this, "Show Diff", FixupSession.ACTION_DIFF)
//      addSeparator(this)
//      addAction(this, "Accept", FixupSession.ACTION_ACCEPT)
//      addSeparator(this)
//      addAction(this, "Reject", FixupSession.ACTION_REJECT)
//      addSeparator(this)
      registerWidgets()
      isHeaderGroup = true
    }
  }

  fun createBlockGroup(editId: String?): LensWidgetGroup {
    logger.warn("JM: createBlockGroup called with editId: $editId")
    return LensWidgetGroup(session, session.editor).apply {
      addAction(this, "Accept", FixupSession.ACTION_ACCEPT, editId)
      addSeparator(this)
      addAction(this, "Reject", FixupSession.ACTION_REJECT, editId)
      registerWidgets()
      isBlockGroup = true
    }
  }

  fun createErrorGroup(tooltip: String, isDocumentCode: Boolean = false): LensWidgetGroup {
    return LensWidgetGroup(session, session.editor).apply {
      addLogo(this)
      addErrorIcon(this)
      val verb = if (isDocumentCode) "document" else "edit"
      addLabel(this, "Cody failed to $verb this code").apply { hoverText = tooltip }
      addSeparator(this)
      addAction(this, "Dismiss", FixupSession.ACTION_DISMISS)
      addSeparator(this)
      addAction(this, "Open Log", "cody.openLogAction")
      registerWidgets()
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

  private fun addAction(group: LensWidgetGroup, label: String, actionId: String, editId: String? = null) { // range: Range? = null
    group.addWidget(LensAction(group, label, actionId, editId))

    val hotkey = EditCommandPrompt.getShortcutDisplayString(actionId)
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
    const val SEPARATOR = " ∣ "
  }
}
