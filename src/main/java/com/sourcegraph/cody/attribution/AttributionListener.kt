package com.sourcegraph.cody.attribution

import com.sourcegraph.cody.agent.protocol.AttributionSearchResponse

fun interface AttributionListener {
  fun updateAttribution(attribution: AttributionSearchResponse)
}
