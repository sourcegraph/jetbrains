package com.sourcegraph.vcs

class RepoInfo(
    val vcsType: VCSType,
    val remoteUrl:
        String, // E.g. "git@github.com:sourcegraph/sourcegraph.git", with replacements already
    // applied
    val remoteBranchName: String, // E.g. "main"
    val relativePath: String
) { // E.g. "/client/jetbrains/package.json"

  val repoName: String // E.g. "sourcegraph/sourcegraph"
    get() {
      val colonIndex = remoteUrl.lastIndexOf(":")
      val dotIndex = remoteUrl.lastIndexOf(".")
      return remoteUrl.substring(
          colonIndex + 1,
          if (dotIndex == -1 || colonIndex > dotIndex) remoteUrl.length else dotIndex)
    }

  val codeHostUrl: String // E.g. "github.com"
    get() {
      val atIndex = remoteUrl.indexOf("@")
      val colonIndex = remoteUrl.lastIndexOf(":")
      return remoteUrl.substring(
          if (atIndex == -1 && atIndex < colonIndex) 0 else atIndex + 1, colonIndex)
    }
}
