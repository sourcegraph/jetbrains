package com.sourcegraph.find

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanelWithEmptyText
import com.sourcegraph.Icons
import com.sourcegraph.website.CopyAction
import com.sourcegraph.website.FileActionBase
import com.sourcegraph.website.OpenFileAction
import java.awt.BorderLayout
import javax.swing.JComponent

class PreviewPanel(private val project: Project) :
    JBPanelWithEmptyText(BorderLayout()), Disposable {
  private var editorComponent: JComponent? = null
  var previewContent: PreviewContent? = null
    private set

  private var editor: Editor? = null

  init {
    setState(State.NO_PREVIEW_AVAILABLE)
  }

  fun setContent(previewContent: PreviewContent?) {
    val fileContent = previewContent?.content
    if (fileContent == null) {
      setState(State.NO_PREVIEW_AVAILABLE)
      return
    }

    if (editorComponent != null && previewContent == this.previewContent) {
      setState(State.PREVIEW_AVAILABLE)
      return
    }
    this.previewContent = previewContent
    if (editorComponent != null) {
      remove(editorComponent)
    }
    editor?.let(EditorFactory.getInstance()::releaseEditor)

    val editorFactory = EditorFactory.getInstance()
    val document = editorFactory.createDocument(fileContent)
    document.setReadOnly(true)
    editor =
        editorFactory
            .createEditor(
                document, project, previewContent.virtualFile, true, EditorKind.MAIN_EDITOR)
            .also { editor ->
              editor.settings.apply {
                isLineMarkerAreaShown = true
                isFoldingOutlineShown = false
                additionalColumnsCount = 0
                additionalLinesCount = 0
                isAnimatedScrolling = false
                isAutoCodeFoldingEnabled = false
              }
              (editor as EditorImpl).installPopupHandler(
                  ContextMenuPopupHandler.Simple(createActionGroup(editor)))
              setState(State.PREVIEW_AVAILABLE)
              editorComponent =
                  editor.getComponent().also { editorComponent ->
                    add(editorComponent, BorderLayout.CENTER)
                  }
              validate()
              addAndScrollToHighlights(editor, previewContent.absoluteOffsetAndLengths)
            }
  }

  fun setState(state: State) {
    editorComponent?.apply { isVisible = state == State.PREVIEW_AVAILABLE }
    if (state == State.LOADING) {
      emptyText.setText("Loading...")
    } else if (state == State.NO_PREVIEW_AVAILABLE) {
      emptyText.setText("No preview available")
    }
  }

  private fun addAndScrollToHighlights(editor: Editor, absoluteOffsetAndLengths: Array<IntArray>) {
    var firstOffset = -1
    val highlightManager = HighlightManager.getInstance(project)
    for (offsetAndLength in absoluteOffsetAndLengths) {
      if (firstOffset == -1) {
        firstOffset = offsetAndLength[0] + offsetAndLength[1]
      }
      highlightManager.addOccurrenceHighlight(
          editor,
          offsetAndLength[0],
          offsetAndLength[0] + offsetAndLength[1],
          EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES,
          0,
          null)
    }
    if (firstOffset != -1) {
      editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(firstOffset), ScrollType.CENTER)
    }
  }

  override fun dispose() {
    editor?.let(EditorFactory.getInstance()::releaseEditor)
  }

  private fun createActionGroup(editor: Editor): ActionGroup =
      DefaultActionGroup().apply {
        add(
            object :
                DumbAwareAction(
                    "Open File in Editor", "Open file in editor", Icons.SourcegraphLogo) {
              override fun actionPerformed(e: AnActionEvent) {
                try {
                  previewContent?.openInEditorOrBrowser()
                } catch (ex: Exception) {
                  val logger = Logger.getInstance(SelectionMetadataPanel::class.java)
                  logger.warn("Error opening file in editor: " + ex.message)
                }
              }
            })
        add(SimpleEditorFileAction("Open on Sourcegraph", OpenFileAction(), editor))
        add(SimpleEditorFileAction("Copy Sourcegraph File Link", CopyAction(), editor))
      }

  enum class State {
    LOADING,
    PREVIEW_AVAILABLE,
    NO_PREVIEW_AVAILABLE
  }

  internal inner class SimpleEditorFileAction(
      text: String,
      val action: FileActionBase,
      val editor: Editor
  ) : DumbAwareAction(text, text, Icons.CodyLogo) {
    override fun actionPerformed(e: AnActionEvent) {
      val sel = editor.selectionModel
      val selectionStartPosition = sel.selectionStartPosition
      val selectionEndPosition = sel.selectionEndPosition
      val start =
          if (selectionStartPosition != null) editor.visualToLogicalPosition(selectionStartPosition)
          else null
      val end =
          if (selectionEndPosition != null) editor.visualToLogicalPosition(selectionEndPosition)
          else null
      action.actionPerformedFromPreviewContent(project, previewContent, start, end)
    }
  }
}
