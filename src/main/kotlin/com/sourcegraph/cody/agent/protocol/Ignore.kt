package com.sourcegraph.cody.agent.protocol

data class IgnorePolicyPattern(val repoNamePattern: String, val filePathPatterns: List<String>?)

data class IgnorePolicySpec(
    val exclude: List<IgnorePolicyPattern>?,
    val include: List<IgnorePolicyPattern>?,
)
