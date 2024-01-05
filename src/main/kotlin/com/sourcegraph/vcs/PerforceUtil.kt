package com.sourcegraph.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.perforce.application.PerforceVcs
import org.jetbrains.idea.perforce.perforce.connections.P4Connection

object PerforceUtil {
  @Throws(Exception::class) // Returned format: perforce@perforce.company.com:depot-name.perforce
  fun getRemoteRepoUrl(project: Project, file: VirtualFile): String {
    val vcs = PerforceVcs.getInstance(project)
    val rootsByConnections = vcs.rootsByConnections
    val connection =
        rootsByConnections
            .stream()
            .filter { pair: Pair<P4Connection, Collection<VirtualFile>> ->
              pair.getSecond().stream().anyMatch { root: VirtualFile ->
                file.path.startsWith(root.path)
              }
            }
            .map { x: Pair<P4Connection, Collection<VirtualFile>>? -> Pair.getFirst(x) }
            .findFirst()
            .orElseThrow { throw Exception("No Perforce connection found.") }
    val serverUrl = connection.connectionKey.server
    // Remove port if present
    val serverName = serverUrl.split(":")[0]
    val depotName =
        getDepotName(project, connection, file) ?: throw Exception("No depot name found.")
    return "perforce@$serverName:$depotName.perforce"
  }

  @Throws(VcsException::class)
  private fun getDepotName(project: Project, connection: P4Connection, file: VirtualFile): String? {
    val vcs = PerforceVcs.getInstance(project)
    val rootsByConnections = vcs.rootsByConnections
    val pair =
        rootsByConnections
            .stream()
            .filter { x: Pair<P4Connection, Collection<VirtualFile>> ->
              x.getFirst() === connection
            }
            .findFirst()
            .orElse(null)
            ?: return null
    val root =
        pair
            .getSecond()
            .stream()
            .filter { x: VirtualFile -> file.path.startsWith(x.path) }
            .findFirst()
            .orElse(null)
            ?: return null
    var relativePath = file.path.substring(root.path.length)
    if (relativePath.startsWith("/")) {
      relativePath = relativePath.substring(1)
    }
    return relativePath.trim().split("/")[0]
  }
}
