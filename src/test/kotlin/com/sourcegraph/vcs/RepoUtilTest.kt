package com.sourcegraph.vcs

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitVcs
import git4idea.branch.GitBranchesCollection
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
import git4idea.repo.GitBranchTrackInfo
import git4idea.repo.GitRemote
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryFiles
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitSubmoduleInfo
import git4idea.repo.GitUntrackedFilesHolder
import git4idea.status.GitStagingAreaHolder
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class RepoUtilTest : BasePlatformTestCase() {

  @Test
  fun testGetRepoInfo_WithNonNullRepoRootPath() {
    val project = myFixture.project
    val file = myFixture.createFile("file.txt", "")
    val gitRepo = MockedGitRepo(file)

    val vcsUtil = Mockito.mockStatic(VcsUtil::class.java)
    vcsUtil.`when`<VirtualFile> { VcsUtil.getVcsRootFor(project, file) }.thenReturn(file.parent)

    val vcsRepositoryManagerInstance = Mockito.mock(VcsRepositoryManager::class.java)
    `when`(vcsRepositoryManagerInstance.getRepositoryForFile(file)).thenReturn(gitRepo)
    val gitRepositoryManagerInstance = Mockito.mock(GitRepositoryManager::class.java)
    `when`(gitRepositoryManagerInstance.getRepositoryForRoot(file.parent)).thenReturn(gitRepo)

    val vcsRepositoryManager = Mockito.mockStatic(VcsRepositoryManager::class.java)
    vcsRepositoryManager
        .`when`<VcsRepositoryManager> { VcsRepositoryManager.getInstance(project) }
        .thenReturn(vcsRepositoryManagerInstance)

    val gitRepositoryManager = Mockito.mockStatic(GitRepositoryManager::class.java)
    gitRepositoryManager
        .`when`<GitRepositoryManager> { GitRepositoryManager.getInstance(project) }
        .thenReturn(gitRepositoryManagerInstance)

    val repoInfo = RepoUtil.getRepoInfo(project, file)

    assertEquals(VCSType.GIT, repoInfo.vcsType)
    assertEquals("github.com/sourcegraph/jetbrains", repoInfo.remoteUrl)
    assertEquals("mkondratek/great-new-feature", repoInfo.remoteBranchName)
    assertEquals("file.txt", repoInfo.relativePath)
  }
}

class MockedGitRepo(private val file: VirtualFile) : GitRepository {

  private val gitRemote =
      GitRemote(
          /* name = */ GitRemote.ORIGIN,
          /* urls = */ listOf("https://github.com/sourcegraph/jetbrains.git"),
          /* pushUrls = */ emptyList(),
          /* fetchRefSpecs = */ emptyList(),
          /* pushRefSpecs = */ emptyList())

  private val gitLocalBranch = GitLocalBranch("mkondratek/great-new-feature")
  private val gitRemoteBranch = GitStandardRemoteBranch(gitRemote, "mkondratek/great-new-feature")

  override fun dispose() {
    TODO("Not yet implemented")
  }

  override fun getRoot(): VirtualFile = file.parent

  override fun getPresentableUrl(): String {
    TODO("Not yet implemented")
  }

  override fun getProject(): Project {
    TODO("Not yet implemented")
  }

  override fun getState(): Repository.State {
    TODO("Not yet implemented")
  }

  override fun getCurrentBranchName(): String? {
    TODO("Not yet implemented")
  }

  override fun getVcs(): GitVcs {
    TODO("Not yet implemented")
  }

  override fun getCurrentRevision(): String? {
    TODO("Not yet implemented")
  }

  override fun isFresh(): Boolean {
    TODO("Not yet implemented")
  }

  override fun update() {
    TODO("Not yet implemented")
  }

  override fun toLogString(): String {
    TODO("Not yet implemented")
  }

  override fun getGitDir(): VirtualFile {
    TODO("Not yet implemented")
  }

  override fun getRepositoryFiles(): GitRepositoryFiles {
    TODO("Not yet implemented")
  }

  override fun getStagingAreaHolder(): GitStagingAreaHolder {
    TODO("Not yet implemented")
  }

  override fun getUntrackedFilesHolder(): GitUntrackedFilesHolder {
    TODO("Not yet implemented")
  }

  override fun getInfo(): GitRepoInfo {
    TODO("Not yet implemented")
  }

  override fun getCurrentBranch() = gitLocalBranch

  override fun getBranches(): GitBranchesCollection {
    TODO("Not yet implemented")
  }

  override fun getRemotes() = mutableListOf(gitRemote)

  override fun getBranchTrackInfos(): MutableCollection<GitBranchTrackInfo> {
    TODO("Not yet implemented")
  }

  override fun getBranchTrackInfo(localBranchName: String) =
      GitBranchTrackInfo(gitLocalBranch, gitRemoteBranch, false)

  override fun isRebaseInProgress(): Boolean {
    TODO("Not yet implemented")
  }

  override fun isOnBranch(): Boolean {
    TODO("Not yet implemented")
  }

  override fun getSubmodules(): MutableCollection<GitSubmoduleInfo> {
    TODO("Not yet implemented")
  }

  override fun getIgnoredFilesHolder(): GitRepositoryIgnoredFilesHolder {
    TODO("Not yet implemented")
  }
}
