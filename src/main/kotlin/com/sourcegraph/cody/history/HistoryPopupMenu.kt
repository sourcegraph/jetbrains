package com.sourcegraph.cody.history

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.JBPopupMenu
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon

class HistoryPopupMenu(
        onSelect: () -> Unit,
        onDelete: () -> Unit
) : JBPopupMenu() {

    init {
        add(MenuAction("Select", AllIcons.Actions.Download) { onSelect() })
        add(MenuAction("Delete", AllIcons.Actions.GC) { onDelete() })
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