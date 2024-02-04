package com.sourcegraph.cody.ui

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CurrentConfigFeatures
import com.sourcegraph.cody.agent.protocol.AttributionSearchResponse
import com.sourcegraph.cody.attribution.AttributionListener

class AttributionButtonController(val button: ConditionalVisibilityButton) : AttributionListener {

  private val extraUpdates: MutableList<Runnable> = ArrayList()

  companion object {
    fun setup(project: Project): AttributionButtonController {
      val button = ConditionalVisibilityButton("Attribution search")
      button.isEnabled = false // non-clickable
      val currentConfigFeatures: CurrentConfigFeatures =
          project.getService(CurrentConfigFeatures::class.java)
      // Only display the button if attribution is enabled.
      button.visibilityAllowed = currentConfigFeatures.get().attribution
      return AttributionButtonController(button)
    }
  }

  @RequiresEdt
  override fun onAttributionSearchStart() {
    button.toolTipText = "Guard Rails: Running Code Attribution Check..."
  }

  @RequiresEdt
  override fun updateAttribution(attribution: AttributionSearchResponse) {
    if (attribution.error != null) {
      button.text = "Guard Rails API Error"
      button.toolTipText = "Guard Rails API Error: ${attribution.error}."
    } else if (attribution.repoNames.isEmpty()) {
      button.text = "Guard Rails Check Passed"
      button.toolTipText = "Snippet not found on Sourcegraph.com."
    } else {
      val count = "${attribution.repoNames.size}" + if (attribution.limitHit) "+" else ""
      val repoNames = attribution.repoNames.joinToString(separator = ", ")
      button.text = "Guard Rails Check Failed"
      button.toolTipText =
          "Guard Rails Check Failed. Code found in ${count} repositories: ${repoNames}."
    }
    button.updatePreferredSize()
    for (action in extraUpdates) {
      action.run()
    }
  }

  /** Run extra actions on button update, like resizing components. */
  fun onUpdate(action: Runnable) {
    extraUpdates += action
  }
}
