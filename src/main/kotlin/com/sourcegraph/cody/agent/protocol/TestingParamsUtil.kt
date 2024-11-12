package com.sourcegraph.cody.agent.protocol

object TestingParamsUtil {
  val doIncludeTestingParam =
      "true".equals(System.getProperty("cody-agent.panic-when-out-of-sync", "false"))
}
