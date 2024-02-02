package com.sourcegraph.cody.attribution

import com.sourcegraph.cody.agent.protocol.AttributionSearchResponse

/**
 * [AttributionListener] responds to a result of an attribution search.
 *
 * The interface does not convey any contract about execution thread. The caller and callee should
 * make sure of proper execution.
 */
fun interface AttributionListener {
  fun updateAttribution(attribution: AttributionSearchResponse)
}
