package com.sourcegraph.cody.ui

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.sourcegraph.cody.context.RemoteRepoFileType
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class WebPanelFileType : FileType {
  companion object {
    @JvmStatic val INSTANCE = WebPanelFileType()
  }

  override fun getName(): @NonNls String {
    return "SourcegraphWebPanel"
  }

  override fun getDescription(): @NlsContexts.Label String {
    return "Sourcegraph Cody Web Panel"
  }

  override fun getDefaultExtension(): @NlsSafe String {
    return ""
  }

  override fun getIcon(): Icon? {
    return null
  }

  override fun isBinary(): Boolean {
    return true
  }
}
