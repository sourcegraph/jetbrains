package com.sourcegraph.cody.agent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.sourcegraph.cody.config.CodyProjectSettings
import com.sourcegraph.config.ConfigUtil
import com.sourcegraph.vcs.RepoUtil
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class CodyAgentCodebase(val project: Project) {

  private val settings = CodyProjectSettings.getInstance(project)
  private var inferredUrl: CompletableFuture<String> = CompletableFuture()

  init {
    onFileOpened(null)
  }

  fun getUrl(): CompletableFuture<String> =
      if (settings.remoteUrl != null) CompletableFuture.completedFuture(settings.remoteUrl)
      else inferredUrl

  fun onFileOpened(file: VirtualFile?) {
    // This can happen during testing with certain temporary files.
    if (file == null) return
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val repositoryName = RepoUtil.findRepositoryName(project, file)
        if (repositoryName != null && inferredUrl.getNow(null) != repositoryName) {
          inferredUrl.complete(repositoryName)
          CodyAgentService.withAgent(project) {
            it.server.configurationDidChange(ConfigUtil.getAgentConfiguration(project))
          }
        }
      } catch (x: Exception) {
        logger.warn("Error finding repository name for $file", x)
      }
    }
  }

  companion object {
    private val logger = Logger.getInstance(CodyAgentCodebase::class.java)

    @JvmStatic
    fun getInstance(project: Project): CodyAgentCodebase {
      return project.service()
    }
  }
}
