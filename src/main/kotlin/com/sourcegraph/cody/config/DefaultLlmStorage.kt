package com.sourcegraph.cody.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.protocol_generated.Model
import com.sourcegraph.cody.auth.SourcegraphServerPath

@Service(Service.Level.PROJECT)
class DefaultLlmStorage {

  private val storage = HashMap<SourcegraphServerPath, Model>()

  fun store(server: SourcegraphServerPath?, model: Model) {
    server ?: return
    storage[server] = model
  }

  fun get(server: SourcegraphServerPath?): Model? {
    server ?: return null
    return storage[server]
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DefaultLlmStorage = project.service<DefaultLlmStorage>()
  }
}
