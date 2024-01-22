package com.sourcegraph.cody.history

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.JBPopupMenu
import com.sourcegraph.cody.history.state.ChatState
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon

class HistoryPopupMenu(
    onSelect: (ChatState) -> Unit,
    onDelete: (ChatState) -> Unit,
    getSelected: () -> ChatState
) : JBPopupMenu() {

  init {
    add(MenuAction("Select chat") { onSelect(getSelected()) })
    add(MenuAction("Remove chat", AllIcons.Actions.GC) { onDelete(getSelected()) })
  }

  private class MenuAction(
      name: String,
      icon: Icon? = null,
      private val action: (ActionEvent) -> Unit
  ) : AbstractAction(name, icon) {

    override fun actionPerformed(event: ActionEvent?) {
      action(event!!)
    }
  }
}
