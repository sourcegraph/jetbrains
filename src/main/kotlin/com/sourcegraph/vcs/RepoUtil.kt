package com.sourcegraph.vcs

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.orNull
import com.intellij.vcsUtil.VcsUtil
import com.sourcegraph.cody.agent.CodyAgent.Companion.withServer
import com.sourcegraph.cody.agent.CodyAgentServer
import com.sourcegraph.cody.agent.protocol.CloneURL
import com.sourcegraph.cody.config.CodyProjectSettings
import com.sourcegraph.cody.config.CodyProjectSettings.Companion.getInstance
import com.sourcegraph.common.ErrorNotification.show
import com.sourcegraph.vcs.GitUtil.getRemoteBranchName
import com.sourcegraph.vcs.GitUtil.getRemoteRepoUrl
import git4idea.GitVcs
import git4idea.repo.GitRepository
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import org.jetbrains.idea.perforce.perforce.PerforceAuthenticationException
import org.jetbrains.idea.perforce.perforce.PerforceSettings

object RepoUtil {
  private val logger = Logger.getInstance(RepoUtil::class.java)

  // repoInfo returns the Sourcegraph repository URI, and the file path
  // relative to the repository root. If the repository URI cannot be
  // determined, a RepoInfo with empty strings is returned.
  fun getRepoInfo(project: Project, file: VirtualFile): RepoInfo {
    val vcsType = getVcsType(project, file)
    var relativePath = ""
    var remoteUrl = ""
    var remoteBranchName: String? = ""
    val codyProjectSettings = getInstance(project)
    try {
      val repoRootPath = getRepoRootPath(project, file) ?: return RepoInfo(vcsType, "", "", "")

      // Determine file path, relative to repository root.
      relativePath =
          if (file.path.length > repoRootPath.length) file.path.substring(repoRootPath.length + 1)
          else ""
      if (vcsType == VCSType.PERFORCE && relativePath.indexOf('/') != -1) {
        relativePath = relativePath.substring(relativePath.indexOf("/") + 1)
      }
      remoteUrl = getRemoteRepoUrl(project, file)
      remoteUrl = doReplacements(codyProjectSettings, remoteUrl)

      // If the current branch doesn't exist on the remote or if the remote
      // for the current branch doesn't correspond with the sourcegraph remote,
      // use the default branch for the project.
      remoteBranchName = getRemoteBranchName(project, file)
      if (remoteBranchName == null || !remoteUrl.contains(remoteBranchName)) {
        remoteBranchName = codyProjectSettings.defaultBranchName
      }
    } catch (err: Exception) {
      val message =
          if (err is PerforceAuthenticationException) {
            "Perforce authentication error: " + err.message
          } else {
            "Error determining repository info: " + err.message
          }
      show(project, message)
      logger.warn(message)
      logger.warn(err)
    }
    return RepoInfo(
        vcsType, remoteUrl, remoteBranchName ?: codyProjectSettings.defaultBranchName, relativePath)
  }

  fun findRepositoryName(project: Project, currentFile: VirtualFile?): String? {
    val fileFromTheRepository =
        (currentFile ?: getRootFileFromFirstGitRepository(project).orNull()) ?: return null
    return try {
      getRemoteRepoUrlWithoutScheme(project, fileFromTheRepository)
    } catch (e: Exception) {
      getSimpleRepositoryName(project, fileFromTheRepository)
    }
  }

  private fun getSimpleRepositoryName(project: Project, file: VirtualFile): String? {
    try {
      val repository =
          VcsRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return null
      return repository.root.name
    } catch (e: Exception) {
      return null
    }
  }

  private fun doReplacements(codyProjectSettings: CodyProjectSettings, remoteUrl: String): String {
    var remoteUrlWithReplacements = remoteUrl
    val r = codyProjectSettings.remoteUrlReplacements
    val replacements = r.trim().split("\\s*,\\s*")
    if (replacements.size % 2 == 0) {
      for (i in replacements.indices step 2) {
        remoteUrlWithReplacements =
            remoteUrlWithReplacements.replace(replacements[i], replacements[i + 1])
      }
    }
    return remoteUrlWithReplacements
  }

  // Returned format: github.com/sourcegraph/sourcegraph
  // Must be called from non-EDT context
  @Throws(Exception::class)
  private fun getRemoteRepoUrlWithoutScheme(project: Project, file: VirtualFile): String {
    val remoteUrl = getRemoteRepoUrl(project, file)
    var repoName: String
    try {
      val url = URL(remoteUrl)
      repoName = url.host + url.path
    } catch (e: MalformedURLException) {
      repoName = remoteUrl.substring(remoteUrl.indexOf('@') + 1).replaceFirst(":", "/")
    }
    return repoName.replaceFirst(".git$".toRegex(), "")
  }

  // Returned format: git@github.com:sourcegraph/sourcegraph.git
  // Must be called from non-EDT context
  @Throws(Exception::class)
  fun getRemoteRepoUrl(project: Project, file: VirtualFile): String {
    val repository = VcsRepositoryManager.getInstance(project).getRepositoryForFile(file)
    val vcsType = getVcsType(project, file)
    if (vcsType == VCSType.GIT && repository != null) {
      val cloneURL = getRemoteRepoUrl((repository as GitRepository), project)
      val codebaseName =
          withServer(
                  project,
                  { server: CodyAgentServer? ->
                    server!!.convertGitCloneURLToCodebaseName(CloneURL(cloneURL))
                  })
              .join()
              ?: throw Exception(
                  "Failed to convert git clone URL to codebase name for cloneURL: $cloneURL")
      return codebaseName
    }
    if (vcsType == VCSType.PERFORCE) {
      return PerforceUtil.getRemoteRepoUrl(project, file)
    }
    if (repository == null) {
      throw Exception("Could not find repository for file " + file.path)
    }
    throw Exception("Unsupported VCS: " + repository.vcs.name)
  }

  /** Returns the repository root directory for any path within a repository. */
  private fun getRepoRootPath(project: Project, file: VirtualFile): String? {
    val vcsRoot = VcsUtil.getVcsRootFor(project, file)
    return vcsRoot?.path
  }

  /** @return Like "main" */
  private fun getRemoteBranchName(project: Project, file: VirtualFile): String? {
    val repository =
        VcsRepositoryManager.getInstance(project).getRepositoryForFile(file) ?: return null
    if (repository is GitRepository) {
      return getRemoteBranchName(repository)
    }

    // Unknown VCS.
    return null
  }

  fun getVcsType(project: Project, file: VirtualFile): VCSType {
    val repository = VcsRepositoryManager.getInstance(project).getRepositoryForFile(file)
    try {
      Class.forName("git4idea.repo.GitRepository", false, RepoUtil::class.java.getClassLoader())
      if (repository is GitRepository) {
        return VCSType.GIT
      }
    } catch (e: ClassNotFoundException) {
      // Git plugin is not installed.
    }
    try {
      Class.forName(
          "org.jetbrains.idea.perforce.perforce.PerforceSettings",
          false,
          RepoUtil::class.java.getClassLoader())
      if ((PerforceSettings.getSettings(project).getConnectionForFile(File(file.path)) != null)) {
        return VCSType.PERFORCE
      }
    } catch (e: ClassNotFoundException) {
      // Perforce plugin is not installed.
    }
    return VCSType.UNKNOWN
  }

  private fun getRootFileFromFirstGitRepository(project: Project): Optional<VirtualFile?> {
    // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206105769/comments/206091565
    val lock = Object()
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      synchronized(lock) { lock.notify() }
    }
    synchronized(lock) {
      try {
        lock.wait()
      } catch (ignored: InterruptedException) {}
    }
    val firstFoundRepository =
        VcsRepositoryManager.getInstance(project)
            .getRepositories()
            .stream()
            .filter { it: Repository -> (it.vcs.name == GitVcs.NAME) }
            .findFirst()
    return firstFoundRepository.map { obj: Repository -> obj.root }
  }
}
