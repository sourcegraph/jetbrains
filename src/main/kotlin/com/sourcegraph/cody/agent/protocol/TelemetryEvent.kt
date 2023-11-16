package com.sourcegraph.cody.agent.protocol

data class TelemetryEvent(
    val feature: String,
    val action: String,
    val parameters: TelemetryEventParameters? = null
)

// https://sourcegraph.com/npm/sourcegraph/telemetry/-/blob/dist/index.d.ts?L125-182
data class TelemetryEventParameters(
    val version: Int? = 1,
    val metadata: Map<String, Long>?,
    val privateMetadata: Map<String, Any>?,
) {
  val billingMetadata: Map<String, String> =
      mapOf("product" to "exampleBillingProduct", "cateogry" to "exampleBillingCategory")
}
