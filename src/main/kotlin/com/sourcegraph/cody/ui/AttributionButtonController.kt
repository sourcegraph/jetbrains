package com.sourcegraph.cody.ui

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sourcegraph.cody.agent.CurrentConfigFeatures
import com.sourcegraph.cody.agent.protocol.AttributionSearchResponse
import com.sourcegraph.cody.attribution.AttributionListener
import javax.swing.JButton

class AttributionButtonController(val button: JButton): AttributionListener {

  companion object {
    fun setup(project: Project): AttributionButtonController {
      val button = ConditionalVisibilityButton("Attribution search")
      button.setToolTipText("Searching for attribution...")
      val currentConfigFeatures: CurrentConfigFeatures =
        project.getService(CurrentConfigFeatures::class.java)
      button.visibilityAllowed = currentConfigFeatures.get().attribution
      return AttributionButtonController(button)
    }
  }

  @RequiresEdt
  override fun updateAttribution(attribution: AttributionSearchResponse) {
    if (attribution.error != null) {
      button.text = "Attribution unavailable"
    } else if (attribution.repoNames.isEmpty()) {
      button.text = "Attribution successful"
    } else {
      button.text = "Attribution failed"
    }
  }
}