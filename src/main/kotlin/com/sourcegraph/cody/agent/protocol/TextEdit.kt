package com.sourcegraph.cody.agent.protocol

import java.util.*

data class TextEdit(
    // This tag will be 'replace', 'insert', or 'delete'.
    val type: String,

    // Valid for replace & delete.
    val range: Range? = null,

    // Valid for insert.
    var position: Position? = null,

    // Valid for replace & insert.
    val value: String? = null,

    // Unique identified
    var id: String? = UUID.randomUUID().toString(),
)
