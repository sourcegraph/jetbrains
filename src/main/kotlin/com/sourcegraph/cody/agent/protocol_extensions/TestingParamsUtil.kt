package com.sourcegraph.cody.agent.protocol_extensions

object TestingParamsUtil {
  val doIncludeTestingParam =
      "true" == System.getProperty("cody-agent.panic-when-out-of-sync", "false")
}
