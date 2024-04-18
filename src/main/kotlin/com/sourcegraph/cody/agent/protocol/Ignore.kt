package com.sourcegraph.cody.agent.protocol

data class IgnoreForUriParams(val uri: String)

data class IgnoreForUriResponse(
    val policy: String // "use" or "ignore"
)

data class TestingIgnoreOverridePolicy(val uriRe: String, val repoRe: String)
