package com.sourcegraph.cody.auth

import com.sourcegraph.config.ConfigUtil

data class CodyAccount(val server: SourcegraphServerPath, val token: String?) {
  fun isDotcomAccount(): Boolean = server.url.lowercase().startsWith(ConfigUtil.DOTCOM_URL)
}
