package com.sourcegraph.cody.agent.protocol

data class IgnoreTestParams(val uri: String)

data class IgnoreTestResponse(
    val policy: String // "use" or "ignore"
    val transient: Boolean // whether the result should be cached
)

data class IgnorePolicyPattern(val repoNamePattern: String, val filePathPatterns: List<String>?)

data class IgnorePolicySpec(
    val exclude: List<IgnorePolicyPattern>?,
    val include: List<IgnorePolicyPattern>?,
)
