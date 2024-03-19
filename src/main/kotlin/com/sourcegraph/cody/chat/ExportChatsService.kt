package com.sourcegraph.cody.chat

import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.AtomicReference
import com.jetbrains.rd.util.string.printToString
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

@Service(Service.Level.PROJECT)
class ExportChatsService {

  private val localHistory: AtomicReference<CompletableFuture<Any>> =
      AtomicReference(CompletableFuture())
  private val connectionId: AtomicReference<String> = AtomicReference("empty")

  @Synchronized
  fun setLocalHistory(connectionId: String, localHistory: Any?) {
    val str = localHistory.printToString()
    println("setLocalHistory($connectionId, ${str.substring(0, max(str.length, 128))})")
    if (this.connectionId.get() == connectionId && localHistory != null) {
      println("setLocalHistory-complete")
      this.localHistory.get().complete(localHistory)
    }
    println("setLocalHistory-exit")
  }

  @Synchronized
  fun reset(connectionId: String) {
    println("reset($connectionId)")
    this.connectionId.getAndSet(connectionId)
    this.localHistory.getAndSet(CompletableFuture())
    println("reset-exit")
  }

  @Synchronized
  fun getChats(): String {
    println("getChats")
    val anyChats =
        localHistory.get().completeOnTimeout("message" to "failure", 60, TimeUnit.SECONDS).get()
    return gson.toJson(anyChats)
  }

  companion object {
    private val gson = GsonBuilder().create()

    @JvmStatic
    fun getInstance(project: Project): ExportChatsService {
      return project.service<ExportChatsService>()
    }
  }
}
