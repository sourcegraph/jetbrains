package com.sourcegraph.cody.agent.protocol

import com.sourcegraph.cody.error.CodyError

data class RemoteRepoListParams(
    val query: String?,
    val first: Int,
    val after: String?,
)

data class RemoteRepoListResponse(
    val startIndex: Int,
    val count: Int,
    val repos: List<Repo>,
)

data class RemoteRepoFetchState(
    val state: String, // one of: "paused", "fetching", "errored", "complete"
    val error: CodyError,
)