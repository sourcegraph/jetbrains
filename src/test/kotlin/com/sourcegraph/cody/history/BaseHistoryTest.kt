package com.sourcegraph.cody.history

import com.intellij.testFramework.LightPlatformTestCase

abstract class BaseHistoryTest : LightPlatformTestCase() {

    override fun setUp() {
        super.setUp()

        // reset state to remove side effects between test methods
        // todo this should be simpler: find a way to replace service with new instance between test methods
        HistoryService.getInstance().state.activeChatId = null
        HistoryService.getInstance().state.chats.clear()
    }

}