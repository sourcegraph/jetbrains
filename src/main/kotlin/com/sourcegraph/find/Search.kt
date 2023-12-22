package com.sourcegraph.find

class Search(
    val query: String?,
    val isCaseSensitive: Boolean,
    val patternType: String?,
    val selectedSearchContextSpec: String?
)
