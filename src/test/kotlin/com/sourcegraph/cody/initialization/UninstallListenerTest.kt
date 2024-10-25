package com.sourcegraph.cody.initialization

import com.intellij.ide.plugins.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sourcegraph.cody.config.CodyAuthenticationManager
import com.sourcegraph.cody.telemetry.TelemetryV2
import com.sourcegraph.config.ConfigUtil.getPluginId
import io.mockk.*

@Suppress("UnstableApiUsage")
class UninstallListenerTest : BasePlatformTestCase() {

  fun `test plugin uninstall cleans up resources`() {
    val uninstallListener = UninstallListener()

      // Mock dependencies
    // relaxed = true to allow unit returning methods to auto-mock
    val authManager = mockk<CodyAuthenticationManager>(relaxed = true)
    mockkObject(CodyAuthenticationManager)
    every {
      CodyAuthenticationManager.getInstance()
    } returns authManager
    mockkObject(TelemetryV2)
    every {
      TelemetryV2.sendTelemetryEvent(any(), any(), any())
    } returns Unit


    // Execute uninstall
    uninstallListener.runActivity(project)
    val plugin = PluginManagerCore.findPlugin(getPluginId())
    plugin ?: throw Exception("Plugin not found")
    PluginInstaller.prepareToUninstall(plugin)
    verify {
      authManager.setActiveAccount(null)
      authManager.removeAll()
      TelemetryV2.sendTelemetryEvent(
          any(),
          "cody.extension",
          "uninstalled",
          any()
      )
    }
  }
}
