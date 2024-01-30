package com.sourcegraph.cody.history.state

import junit.framework.TestCase

class HistoryStateTest : TestCase() {

  fun `test deserialization has no breaking changes between config versions`() {
    /**
     * IMPORTANT: Setting files (config/options/cody_history.xml) contain internal class names as
     * tags, similar to <list><ChatState/></list>. The following assertions are here to ensure that
     * we are not introducing breaking changes.
     */
    assertEquals("ChatState", ChatState::class.simpleName)
    assertEquals("HistoryState", HistoryState::class.simpleName)
    assertEquals("MessageState", MessageState::class.simpleName)
  }
}
