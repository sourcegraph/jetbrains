package com.sourcegraph.cody.history.listener

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class DoubleClickListener(private val action: () -> Unit) : MouseAdapter() {

  override fun mouseClicked(e: MouseEvent?) {
    if (e?.clickCount == 2) action()
  }
}
