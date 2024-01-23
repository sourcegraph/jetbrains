import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.system.CpuArch
import com.sourcegraph.cody.agent.CodyAgentClient
import com.sourcegraph.cody.agent.CodyAgentException
import com.sourcegraph.cody.agent.CodyAgentServer
import com.sourcegraph.cody.agent.protocol.*
import com.sourcegraph.config.ConfigUtil
import org.eclipse.lsp4j.jsonrpc.Launcher
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.*
import java.util.*
import java.util.concurrent.*

/**
 * Orchestrator for the Cody agent, which is a Node.js program that implements the prompt logic for
 * Cody. The agent communicates via a JSON-RPC protocol that is documented in the file
 * "cody/agent/src/protocol.ts".
 */
class CodyAgent
private constructor(
    val client: CodyAgentClient,
    val server: CodyAgentServer,
    val launcher: Launcher<CodyAgentServer>,
    private val agentProcess: Process,
    private val listeningToJsonRpc: Future<Void?>
) {

  fun shutdown(): CompletableFuture<Void> {
    return server.shutdown().thenAccept {
      server.exit()
      logger.warn("Cody Agent shut down")
      listeningToJsonRpc.cancel(true)
      agentProcess.destroyForcibly()
    }
  }

  fun isConnected(): Boolean {
    // NOTE(olafurpg): there are probably too many conditions below. We test multiple conditions
    // because we don't know 100% yet what exactly constitutes a "connected" state. Out of
    // abundance of caution, we check everything we can think of.
    return agentProcess.isAlive && !listeningToJsonRpc.isDone && !listeningToJsonRpc.isCancelled
  }

  companion object {
    private val logger = Logger.getInstance(CodyAgent::class.java)
    private val PLUGIN_ID = PluginId.getId("com.sourcegraph.jetbrains")
    @JvmField val executorService: ExecutorService = Executors.newCachedThreadPool()

    fun create(project: Project): CompletableFuture<CodyAgent> {
      try {
        val agentProcess = startAgentProcess()
        val client = CodyAgentClient()
        val launcher = startAgentLauncher(agentProcess, client)
        val server = launcher.remoteProxy
        val listeningToJsonRpc = launcher.startListening()

        try {
          return server
              .initialize(
                  ClientInfo(
                      version = ConfigUtil.getPluginVersion(),
                      workspaceRootUri = ConfigUtil.getWorkspaceRootPath(project).toUri(),
                      extensionConfiguration = ConfigUtil.getAgentConfiguration(project)))
              .thenApply { info ->
                logger.info("Connected to Cody agent " + info.name)
                server.initialized()
                CodyAgent(client, server, launcher, agentProcess, listeningToJsonRpc)
              }
        } catch (e: Exception) {
          logger.warn("Failed to send 'initialize' JSON-RPC request Cody agent", e)
          throw e
        }
      } catch (e: Exception) {
        logger.warn("Unable to start Cody agent", e)
        throw e
      }
    }

    private fun startAgentProcess(): Process {
      val binary = agentBinary()
      logger.info("starting Cody agent " + binary.absolutePath)
      val command: List<String> =
          if (System.getenv("CODY_DIR") != null) {
            val script = File(System.getenv("CODY_DIR"), "agent/dist/index.js")
            logger.info("using Cody agent script " + script.absolutePath)
            listOf("node", "--enable-source-maps", script.absolutePath)
          } else {
            listOf(binary.absolutePath)
          }

      val processBuilder = ProcessBuilder(command)
      if (java.lang.Boolean.getBoolean("cody.accept-non-trusted-certificates-automatically") ||
          ConfigUtil.getShouldAcceptNonTrustedCertificatesAutomatically()) {
        processBuilder.environment()["NODE_TLS_REJECT_UNAUTHORIZED"] = "0"
      }

      val process =
          processBuilder
              .redirectErrorStream(true)
              .redirectError(ProcessBuilder.Redirect.PIPE)
              .start()

      // Redirect agent stderr into idea.log by buffering line by line into `logger.warn()`
      // statements. Without this logic, the stderr output of the agent process is lost if the
      // process
      // fails to start for some reason. We use `logger.warn()` because the agent shouldn't print
      // much
      // normally (excluding a few noisy messages during initialization), it's mostly used to report
      // unexpected errors.
      Thread { process.errorStream.bufferedReader().forEachLine { line -> logger.warn(line) } }
          .start()

      return process
    }

    @Throws(IOException::class, CodyAgentException::class)
    private fun startAgentLauncher(
        agentProcess: Process,
        client: CodyAgentClient
    ): Launcher<CodyAgentServer> {
      return Launcher.Builder<CodyAgentServer>()
          .configureGson { gsonBuilder ->
            gsonBuilder
                // emit `null` instead of leaving fields undefined because Cody
                // in VSC has
                // many `=== null` checks that return false for undefined fields.
                .serializeNulls()
                .registerTypeAdapter(CompletionItemID::class.java, CompletionItemIDSerializer)
                .registerTypeAdapter(ContextFile::class.java, contextFileDeserializer)
          }
          .setRemoteInterface(CodyAgentServer::class.java)
          .traceMessages(traceWriter())
          .setExecutorService(executorService)
          .setInput(agentProcess.inputStream)
          .setOutput(agentProcess.outputStream)
          .setLocalService(client)
          .create()
    }

    private fun binarySuffix(): String {
      return if (SystemInfoRt.isWindows) ".exe" else ""
    }

    private fun agentBinaryName(): String {
      val os = if (SystemInfoRt.isMac) "macos" else if (SystemInfoRt.isWindows) "win" else "linux"
      // Only use x86 for macOS because of this issue here https://github.com/vercel/pkg/issues/2004
      // TLDR; we're not able to run macos-arm64 binaries when they're created on ubuntu-latest
      val arch = if (CpuArch.isArm64()) "arm64" else "x64"
      return "agent-" + os + "-" + arch + binarySuffix()
    }

    private fun agentDirectory(): Path? {
      val fromProperty = System.getProperty("cody-agent.directory", "")
      if (fromProperty.isNotEmpty()) {
        return Paths.get(fromProperty)
      }
      val plugin = PluginManagerCore.getPlugin(PLUGIN_ID) ?: return null
      return plugin.pluginPath
    }

    @Throws(CodyAgentException::class)
    private fun agentBinary(): File {
      val pluginPath =
          agentDirectory()
              ?: throw CodyAgentException("Sourcegraph Cody + Code Search plugin path not found")
      val binarySource = pluginPath.resolve("agent").resolve(agentBinaryName())
      if (!Files.isRegularFile(binarySource)) {
        throw CodyAgentException(
            "Cody agent binary not found at path " + binarySource.toAbsolutePath())
      }
      return try {
        val binaryTarget = Files.createTempFile("cody-agent", binarySuffix())
        logger.info("extracting Cody agent binary to " + binaryTarget.toAbsolutePath())
        Files.copy(binarySource, binaryTarget, StandardCopyOption.REPLACE_EXISTING)
        val binary = binaryTarget.toFile()
        if (binary.setExecutable(true)) {
          binary.deleteOnExit()
          binary
        } else {
          throw CodyAgentException("failed to make executable " + binary.absolutePath)
        }
      } catch (e: IOException) {
        throw CodyAgentException("failed to create agent binary", e)
      }
    }

    private fun traceWriter(): PrintWriter? {
      val tracePath = System.getProperty("cody-agent.trace-path", "")
      if (tracePath.isNotEmpty()) {
        val trace = Paths.get(tracePath)
        try {
          Files.createDirectories(trace.parent)
          return PrintWriter(
              Files.newOutputStream(
                  trace, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
        } catch (e: IOException) {
          logger.warn("unable to trace JSON-RPC debugging information to path $tracePath", e)
        }
      }
      return null
    }
  }
}
