package com.sourcegraph.cody.initialization

import com.intellij.ide.plugins.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.telemetry.TelemetryV2
import com.sourcegraph.config.ConfigUtil
import io.mockk.*

@Suppress("UnstableApiUsage")
class UninstallListenerTest : BasePlatformTestCase() {

  private val uninstallListener = UninstallListener()

  // Mock dependencies
  // relaxed = true to allow unit returning methods to auto-mock
  private val authManager = mockk<CodyAuthenticationManager>(relaxed = true)

  override fun setUp() {
    super.setUp()

    // setup mock objects
    mockkObject(CodyAuthenticationManager)
    every { CodyAuthenticationManager.getInstance() } returns authManager
    mockkObject(TelemetryV2)
    every { TelemetryV2.sendTelemetryEvent(any(), any(), any()) } returns Unit
  }

  private fun getPlugin() =
      PluginManagerCore.findPlugin(ConfigUtil.getPluginId()) ?: throw Exception("Plugin not found")

  fun `test plugin uninstall cleans up resources`() {
    // Execute uninstall
    uninstallListener.runActivity(project)
    val plugin = getPlugin()
    PluginInstaller.prepareToUninstall(plugin)
    verify {
      authManager.setActiveAccount(null)
      authManager.removeAll()
      TelemetryV2.sendTelemetryEvent(any(), "cody.extension", "uninstalled", any())
    }
  }

  fun `test plugin uninstall does nothing for unrelated plugins`() {
    // Execute uninstall
    val plugin = getPlugin()
    uninstallListener.runActivity(project)

    // Now mock out config util so that it returns a different plugin id
    // so that the UninstallListener thinks it's a different plugin
    mockkStatic(ConfigUtil::getPluginId)
    every { ConfigUtil.getPluginId() } returns PluginId.getId("com.sourcegraph.cody.test")

    PluginInstaller.prepareToUninstall(plugin)
    // Remove the static method mock so that it doesn't interfere with other tests
    unmockkStatic(ConfigUtil::getPluginId)

    // Verify that the uninstall listener didn't do anything
    verify(exactly = 0) {
      authManager.setActiveAccount(null)
      authManager.removeAll()
      TelemetryV2.sendTelemetryEvent(any(), "cody.extension", "uninstalled", any())
    }
  }
}
