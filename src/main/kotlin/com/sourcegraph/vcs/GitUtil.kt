package com.sourcegraph.vcs

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.stream.Collectors

object GitUtil {
  @Throws(Exception::class) // Returned format: git@github.com:sourcegraph/sourcegraph.git
  fun getRemoteRepoUrl(repository: GitRepository, project: Project): String {
    val remote =
        getBestRemote(repository, project)
            ?: throw Exception("No configured git remote for \"sourcegraph\" or \"origin\".")
    return remote.firstUrl ?: throw Exception("No URL found for git remote \"${remote.name}\".")
  }

  fun getRemoteBranchName(repository: GitRepository): String? {
    val localBranch = repository.currentBranch ?: return null
    val remoteBranch = localBranch.findTrackedBranch(repository) ?: return null
    return remoteBranch.nameForRemoteOperations
  }

  private fun getBestRemote(repository: GitRepository, project: Project): GitRemote? {
    val sourcegraphRemote = getRemote(repository, project, "sourcegraph")
    return sourcegraphRemote ?: getRemote(repository, project, "origin")
  }

  private fun getRemote(repository: Repository, project: Project, remoteName: String): GitRemote? {
    val gitRepository =
        GitRepositoryManager.getInstance(project).getRepositoryForRoot(repository.root)
            ?: return null
    val matchingRemotes =
        gitRepository.remotes
            .stream()
            .filter { x: GitRemote -> x.name == remoteName }
            .collect(Collectors.toList())
    return try {
      matchingRemotes[0]
    } catch (e: IndexOutOfBoundsException) {
      null
    }
  }
}
