package com.sourcegraph.cody.edit.lenses

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.sourcegraph.cody.agent.protocol_generated.ProtocolCodeLens
import java.awt.Point
import java.net.URI

typealias TaskId = String

interface LensListener {
  fun onLensesUpdate(uri: URI, codeLenses: List<ProtocolCodeLens>)
}

@Service(Service.Level.PROJECT)
class LensesService(val project: Project) {
  @Volatile private var lensGroups = mutableMapOf<URI, List<ProtocolCodeLens>>()

  private val listeners = mutableListOf<LensListener>()

  fun getTaskIdsOfFirstVisibleLens(editor: Editor): TaskId? {
    val lenses = getLenses(editor).sortedBy { it.range.start.line }
    val visibleArea = editor.scrollingModel.visibleArea
    val startPosition = editor.xyToVisualPosition(visibleArea.location)
    val endPosition =
        editor.xyToVisualPosition(
            Point(visibleArea.x + visibleArea.width, visibleArea.y + visibleArea.height))
    val cmd = lenses.find { it.range.start.line in (startPosition.line..endPosition.line) }?.command
    val taskId = (cmd?.arguments?.firstOrNull() as com.google.gson.JsonPrimitive).asString
    return taskId
  }

  fun addListener(listener: LensListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: LensListener) {
    listeners.remove(listener)
  }

  fun updateLenses(uriString: String, codeLens: List<ProtocolCodeLens>) {
    val uri = URI.create(uriString) ?: return
    synchronized(this) { lensGroups[uri] = codeLens }

    listeners.forEach { it.onLensesUpdate(uri, codeLens) }

    runInEdt {
      if (project.isDisposed) return@runInEdt
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      CodeVisionInitializer.getInstance(project)
          .getCodeVisionHost()
          .invalidateProvider(
              CodeVisionHost.LensInvalidateSignal(
                  editor, EditCodeVisionProvider.allEditProviders().map { it.id }))
    }
  }

  fun getLenses(editor: Editor): List<ProtocolCodeLens> {
    val document = editor.document
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return emptyList()
    val virtualFile = file.viewProvider.virtualFile
    val uri = URI.create(virtualFile.url) ?: return emptyList()

    synchronized(this) {
      return lensGroups[uri] ?: emptyList()
    }
  }

  companion object {
    fun getInstance(project: Project): LensesService {
      return project.service<LensesService>()
    }
  }
}
