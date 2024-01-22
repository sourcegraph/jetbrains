package com.sourcegraph.cody.history.listener

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class RightClickListener(private val action: (MouseEvent) -> Unit) : MouseAdapter() {

  override fun mousePressed(e: MouseEvent?) {
    if (SwingUtilities.isRightMouseButton(e)) action(e!!)
  }
}
