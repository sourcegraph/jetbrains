package com.sourcegraph.cody.autocomplete.action

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sourcegraph.cody.autocomplete.CodyAutocompleteManager

class TriggerAutocompleteActionTest : BasePlatformTestCase() {

  fun `test autocomplete`() {
    CodyAutocompleteManager.instance.disableAsyncAutocomplete()
    myFixture.configureByText("script.py", "print(\"Hello <caret>")
    myFixture.testAction(TriggerAutocompleteAction())

    val telemetry = CodyAutocompleteManager.instance.currentAutocompleteTelemetry!!.params()!!
    assertEquals(telemetry.type, "inline")
    assertEquals(telemetry.languageId, "python")
    assertEquals(telemetry.lineCount, 1)
  }
}
