package com.sourcegraph.cody.chat

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.AtomicReference
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class ExportChatsService {

  private val localHistory: AtomicReference<CompletableFuture<Any>> =
      AtomicReference(CompletableFuture())
  private val chatInternalId: AtomicReference<String> = AtomicReference("empty")

  @Synchronized
  fun setLocalHistory(chatInternalId: String, localHistory: Any?) {
    if (this.chatInternalId.get() == chatInternalId) {
      this.localHistory.get().complete(localHistory)
    }
  }

  @Synchronized
  fun reset(chatInternalId: String) {
    this.chatInternalId.getAndSet(chatInternalId)
    this.localHistory.getAndSet(CompletableFuture())
  }

  @Synchronized
  fun getChats(): Any? {
    chatInternalId.getAndSet("empty")
    return localHistory.get().get()
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): ExportChatsService {
      return project.service<ExportChatsService>()
    }
  }
}
