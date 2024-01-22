package com.sourcegraph.cody.history.listener

import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

class EnterListener(private val action: () -> Unit) : KeyAdapter() {

  override fun keyReleased(e: KeyEvent?) {
    if (e?.keyCode == KeyEvent.VK_ENTER) action()
  }
}
