package com.sourcegraph.cody

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class DiagnosticCollectorHighlightingPass(val file: PsiFile, document: Document) :
    TextEditorHighlightingPass(file.project, document) {
  // TODO make sure that the highlighting logic and the agent requests don't run in EDT

  private val logger = Logger.getInstance(DiagnosticCollectorHighlightingPass::class.java)

  private var infoHolder: HighlightInfoHolder? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    // FIXME find the default visitor
    val highlightVisitors = HighlightVisitor.EP_HIGHLIGHT_VISITOR.extensionList
    logger.warn("Found ${highlightVisitors.size} highlight visitors")
    for (x in highlightVisitors) {
      logger.warn(x.javaClass.name)
    }

    logger.warn("Collecting highlight information")
    infoHolder = createInfoHolder(file)
    // TODO when the HighlightVisitor is instantiated, use it to populate the infoHolder
  }

  override fun doApplyInformationToEditor() {
    logger.warn("Applying highlight information")
    if (infoHolder == null) {
      logger.warn("There is no HighlightInfoHolder")
      return
    } else {
      logger.warn("Collected ${infoHolder!!.size()} highlight infos")
    }
    // TODO Convert this information to a list of ProtocolDiagnostic and send them to the agent
  }

  private fun createInfoHolder(file: PsiFile?): HighlightInfoHolder {
    val filters = HighlightInfoFilter.EXTENSION_POINT_NAME.extensionList
    logger.warn("Processing ${filters.size} highlight filters")
    val holder = HighlightInfoHolder(file!!, *filters.toTypedArray())
    logger.warn("Created a new HighlightInfoHolder")
    return holder
  }
}

class DiagnosticCollectorHighlightingPassFactory : TextEditorHighlightingPassFactoryRegistrar {
  private val factory: TextEditorHighlightingPassFactory =
      TextEditorHighlightingPassFactory { file, editor ->
        DiagnosticCollectorHighlightingPass(file, editor.document)
      }

  override fun registerHighlightingPassFactory(
      registrar: TextEditorHighlightingPassRegistrar,
      project: Project
  ) {
    registrar.registerTextEditorHighlightingPass(
        factory, null, arrayOf(Pass.UPDATE_ALL).toIntArray(), false, -1)
  }
}
