package com.sourcegraph.cody.agent.protocol

data class CodyError(val message: String, val cause: CodyError? = null, val stack: String? = null)
