package com.sourcegraph.cody.context.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.BaseCompletionService
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerPosition
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.SoftWrapsEditorCustomization
import com.intellij.util.LocalTimeCounter
import com.intellij.util.textCompletion.TextCompletionUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.sourcegraph.utils.CodyEditorUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.border.CompoundBorder


const val MAX_REMOTE_REPOSITORY_COUNT = 10

// TODO:
// Per https://intellij-support.jetbrains.com/hc/zh-cn/community/posts/206140139-How-to-trigger-annotator-without-changing-file-
// The following probably re-polls the annotators:
// DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor)

private val RemoteRepoLanguage = object : Language("SourcegraphRemoteRepoList") {}

class RemoteRepoFileType : LanguageFileType(RemoteRepoLanguage) {
  companion object {
    @JvmStatic
    val INSTANCE = RemoteRepoFileType()
  }

  override fun getName(): String {
    return "SourcegraphRemoteRepoListFile"
  }

  override fun getDescription(): String {
    return "A list of Sourcegraph repository indexes"
  }

  override fun getDefaultExtension(): String {
    return "todoremove"
  }

  override fun getIcon(): Icon? {
    return null
  }
}

class RemoteRepoTokenType(debugName: @NonNls String) : IElementType(debugName, RemoteRepoLanguage) {
  override fun toString(): String {
    return "RemoteRepoTokenType." + super.toString()
  }

  companion object {
    val REPO = RemoteRepoTokenType("REPO")
    val SEPARATOR = RemoteRepoTokenType("SEPARATOR")
    val EOF = RemoteRepoTokenType("EOF")
  }
}

class RemoteRepoFile(viewProvider: FileViewProvider?) :
  PsiFileBase(viewProvider!!, RemoteRepoLanguage) {
  override fun getFileType(): FileType {
    return RemoteRepoFileType.INSTANCE
  }

  override fun toString(): String {
    return "Remote Repo File"
  }
}

private enum class LexerState(val value: Int) {
  IN_REPO(1),
  IN_SEPARATOR(2),
  EOF(3);

  companion object {
    fun fromInt(value: Int): LexerState? = values().find { it.value == value }
  }
}

internal class RemoteRepoListParserDefinition : ParserDefinition {
  override fun createLexer(project: Project): Lexer {
    return object : Lexer() {
      var buffer: CharSequence = ""
      var startOffset: Int = 0
      var endOffset: Int = 0
      var state: LexerState = LexerState.EOF
      var offset: Int = 0

      override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        offset = startOffset
        state = LexerState.fromInt(initialState) ?: stateAtOffset()
      }

      override fun getState(): Int {
        return this.state.value
      }

      override fun getTokenType(): IElementType? {
        return when (state) {
          LexerState.IN_REPO -> RemoteRepoTokenType.REPO
          LexerState.IN_SEPARATOR -> RemoteRepoTokenType.SEPARATOR
          LexerState.EOF -> null
        }
      }

      override fun getTokenStart(): Int {
        return this.offset
      }

      override fun getTokenEnd(): Int {
        val index = when (tokenType) {
          RemoteRepoTokenType.REPO -> buffer.indexOfAny(charArrayOf(' ', '\t', '\r', '\n'), offset)
          RemoteRepoTokenType.SEPARATOR -> {
            val subIndex = buffer.subSequence(offset, buffer.length).indexOfFirst { ch -> " \t\r\n".indexOf(ch) == -1 }
            if (subIndex == -1) { -1 } else { offset + subIndex }
          }
          RemoteRepoTokenType.EOF -> return buffer.length
          else -> throw RuntimeException("unexpected token type $tokenType lexing repo list")
        }
        return if (index == -1) { buffer.length } else { index }
      }

      override fun advance() {
        this.offset = this.tokenEnd
        this.state = stateAtOffset()
      }

      fun stateAtOffset(): LexerState {
        val ch = nextChar()
        return when {
          ch == null -> LexerState.EOF
          ch.isWhitespace() -> LexerState.IN_SEPARATOR
          else -> LexerState.IN_REPO
        }
      }

      fun nextChar(): Char? {
        // TODO: Should this be buffer.length or endOffset?
        return if (offset == buffer.length) { null } else { buffer[offset] }
      }

      override fun getCurrentPosition(): LexerPosition {
        val snapState = this.state.value
        val snapOffset = this.offset

        return object : LexerPosition {
          override fun getOffset(): Int {
            return snapOffset
          }

          override fun getState(): Int {
            return snapState
          }
        }
      }

      override fun restore(position: LexerPosition) {
        this.offset = position.offset
        this.state = LexerState.fromInt(position.state) ?: stateAtOffset()
      }

      override fun getBufferSequence(): CharSequence {
        return buffer
      }

      override fun getBufferEnd(): Int {
        return endOffset
      }
    }
  }

  override fun getCommentTokens(): TokenSet {
    return TokenSet.EMPTY
  }

  override fun getStringLiteralElements(): TokenSet {
    return TokenSet.EMPTY
  }

  override fun createParser(project: Project): PsiParser {
    return PsiParser { root, builder ->
      val repoList = builder.mark()
      while (!builder.eof()) {
        val tokenType = builder.tokenType
        when (builder.tokenType) {
          RemoteRepoTokenType.REPO -> {
            val mark = builder.mark()
            builder.advanceLexer()
            mark.done(RemoteRepoTokenType.REPO)
          }

          RemoteRepoTokenType.SEPARATOR -> {
            builder.advanceLexer()
          }

          else -> {
            builder.error("Unexpected token type: $tokenType")
            builder.advanceLexer()
          }
        }
      }
      repoList.done(root)
      builder.treeBuilt
    }
  }

  override fun getFileNodeType(): IFileElementType {
    return FILE
  }

  override fun createFile(viewProvider: FileViewProvider): PsiFile {
    return RemoteRepoFile(viewProvider)
  }

  override fun createElement(node: ASTNode): PsiElement {
    return ASTWrapperPsiElement(node)
  }

  companion object {
    val FILE: IFileElementType = IFileElementType(RemoteRepoLanguage)
  }
}

class RemoteRepoAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    // TODO: Messages/tooltips are not appearing on hover
    when (element.elementType) {
      RemoteRepoTokenType.REPO -> {
        // Checks:
        // - Repositories are known
      }
      RemoteRepoListParserDefinition.FILE -> {
        val seen = mutableSetOf<String>()
        var firstTruncatedElement: PsiElement? = null
        element.children.filter {
          it.elementType == RemoteRepoTokenType.REPO
        }.forEach { repo ->
          val name = repo.text
          if (seen.contains(name)) {
            // TODO: L10N
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "Duplicate repository name").tooltip("Duplicate repository name").range(repo).create()
          } else if (seen.size == MAX_REMOTE_REPOSITORY_COUNT) {
            firstTruncatedElement = firstTruncatedElement ?: repo
          }
          seen.add(name)
        }
        if (firstTruncatedElement != null) {
          // TODO: L10N
          holder.newAnnotation(HighlightSeverity.WARNING, "Add up to $MAX_REMOTE_REPOSITORY_COUNT repositories").tooltip("Too many repositories").range(
            TextRange(firstTruncatedElement!!.startOffset, element.endOffset)
          ).create()
        }
      }
    }
  }
}

class RemoteRepoPopupController(val project: Project) {
  fun createPopup(width: Int): JBPopup {
    val initialValue = "" // TODO: Parse initial value from repo list

    val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
      "RepositoryList",
      RemoteRepoFileType.INSTANCE, initialValue, LocalTimeCounter.currentTime(), true, false
    )
    psiFile.putUserData<Boolean>(BaseCompletionService.FORBID_WORD_COMPLETION, false)
    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(psiFile, true)

    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)!!

    val editor = EditorFactory.getInstance().createEditor(document, project)
    editor.putUserData<Boolean>(AutoPopupController.ALWAYS_AUTO_POPUP, true)
    editor.putUserData<Boolean>(CodyEditorUtil.KEY_EDITOR_WANTS_AUTOCOMPLETE, false)
    if (editor is EditorEx) {
      editor.apply {
        SoftWrapsEditorCustomization.ENABLED.customize(this)
        setHorizontalScrollbarVisible(false)
        setVerticalScrollbarVisible(true)
        highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, RemoteRepoFileType.INSTANCE)
        addFocusListener(object : FocusChangeListener {
          override fun focusGained(editor: Editor) {
            super.focusGained(editor)
            val project = editor.project
            if (project != null) {
              AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            }
          }
        })
      }
    }
    editor.settings.apply {
      additionalLinesCount = 0
      additionalColumnsCount = 1
      isRightMarginShown = false
      setRightMargin(-1)
      isFoldingOutlineShown = false
      isLineNumbersShown = false
      isLineMarkerAreaShown = false
      isIndentGuidesShown = false
      isVirtualSpace = false
      isWheelFontChangeEnabled = false
      isAdditionalPageAtBottom = false
      lineCursorWidth = 1
    }
    editor.contentComponent.apply {
      border = CompoundBorder(JBUI.Borders.empty(2), border)
    }

    val panel = JPanel(BorderLayout()).apply {
      add(editor.component, BorderLayout.CENTER)
    }
    val shortcut = KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.shortcuts)
    val scaledHeight = JBDimension(0, 100).height
    val popup = (JBPopupFactory.getInstance().createComponentPopupBuilder(panel, editor.contentComponent).apply {
      setAdText("Select up to $MAX_REMOTE_REPOSITORY_COUNT repositories, use $shortcut to finish")
      setCancelOnClickOutside(false) // TODO
      setMayBeParent(true)
      setMinSize(Dimension(width, scaledHeight))
      setRequestFocus(true)
      setResizable(true)
      addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          EditorFactory.getInstance().releaseEditor(editor)
        }
      })
    }).createPopup()

    // TODO: The popup "ad text" is gratuitously wrapped.

    val okAction = object : DumbAwareAction() {
      override fun actionPerformed(event: AnActionEvent) {
        unregisterCustomShortcutSet(popup.content)
        popup.closeOk(event.inputEvent)
      }
    }
    okAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, popup.content)

    return popup
  }
}