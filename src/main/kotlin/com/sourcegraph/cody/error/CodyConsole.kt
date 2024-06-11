package com.sourcegraph.cody.error

// @Service(Service.Level.PROJECT)
// class CodyConsole(project: Project) {
//  private val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
//  private val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Problems View")
//  var content: Content? = null
//
//  val isEnabled =
//      System.getProperty("sourcegraph.verbose-logging") == "true" ||
//          System.getProperty("cody-agent.panic-when-out-of-sync") == "true"
//
//  fun addMessage(message: DebugMessage) {
//    if (isEnabled) {
//      runInEdt {
//        if (message.message.contains("ERROR") || message.message.contains("PANIC")) {
//          toolWindow?.show()
//          content?.let { toolWindow?.contentManager?.setSelectedContent(it) }
//          consoleView.print(
//              "${message.channel}: ${message.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
//        } else {
//          consoleView.print(
//              "${message.channel}: ${message.message}\n", ConsoleViewContentType.NORMAL_OUTPUT)
//        }
//      }
//    }
//  }
//
//  init {
//    if (isEnabled) {
//      runInEdt {
//        val factory = toolWindow?.contentManager?.factory
//        content = factory?.createContent(consoleView.component, "Cody Console", true)
//        content?.let { toolWindow?.contentManager?.addContent(it) }
//      }
//    }
//  }
//
//  companion object {
//    @JvmStatic
//    fun getInstance(project: Project): CodyConsole {
//      return project.service<CodyConsole>()
//    }
//  }
// }
