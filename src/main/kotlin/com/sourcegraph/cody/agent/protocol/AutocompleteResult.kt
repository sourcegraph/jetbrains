package com.sourcegraph.cody.agent.protocol

import com.sourcegraph.cody.agent.protocol_generated.AutocompleteItem

data class AutocompleteResult(val items: List<AutocompleteItem>)
