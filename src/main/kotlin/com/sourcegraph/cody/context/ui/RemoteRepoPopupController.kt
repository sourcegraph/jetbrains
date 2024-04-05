package com.sourcegraph.cody.context.ui

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.*
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerPosition
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.ui.SoftWrapsEditorCustomization
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
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
    return ""
  }

  override fun getIcon(): Icon? {
    return null
  }
}

private class TokenType(debugName: @NonNls String) : IElementType(debugName, RemoteRepoLanguage) {
  override fun toString(): String {
    return "RemoteRepoTokenType." + super.toString()
  }

  companion object {
    val REPO = TokenType("REPO")
    val SEPARATOR = TokenType("SEPARATOR")
    val EOF = TokenType("EOF")
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
          LexerState.IN_REPO -> TokenType.REPO
          LexerState.IN_SEPARATOR -> TokenType.SEPARATOR
          LexerState.EOF -> null
        }
      }

      override fun getTokenStart(): Int {
        return this.offset
      }

      override fun getTokenEnd(): Int {
        val index = when (tokenType) {
          TokenType.REPO -> buffer.indexOfAny(charArrayOf(' ', '\t', '\r', '\n'), offset)
          TokenType.SEPARATOR -> {
            val subIndex = buffer.subSequence(offset, buffer.length).indexOfFirst { ch -> " \t\r\n".indexOf(ch) == -1 }
            if (subIndex == -1) { -1 } else { offset + subIndex }
          }
          TokenType.EOF -> return buffer.length
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
          TokenType.REPO -> {
            val mark = builder.mark()
            builder.advanceLexer()
            mark.done(TokenType.REPO)
          }

          TokenType.SEPARATOR -> {
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

class RemoteRepoAnnotator : Annotator, DumbAware {
  init {
    println("creating a RemoteRepoAnnotator")
  }
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    println("Annotate! ${element}")
    holder.newAnnotation(HighlightSeverity.ERROR, "Turn again, Caesar").apply {
      range(TextRange(0, holder.currentAnnotationSession.file.textLength)).create()
    }
  }
}

class RemoteRepoPopupController(val project: Project) {
  private val completionProvider = RemoteRepoCompletionProvider(project)
  fun createPopup(width: Int): JBPopup {
    val initialValue = "" // TODO: Parse initial value from repo list
    val textField = TextFieldWithAutoCompletion(project, completionProvider, false, initialValue).apply {
      border = CompoundBorder(JBUI.Borders.empty(2), border)
      addSettingsProvider { editor: EditorEx? ->
        SoftWrapsEditorCustomization.ENABLED.customize(
          editor!!
        )
        // TODO: Should this display a placeholder?
      }
      fileType = RemoteRepoFileType.INSTANCE
      setOneLineMode(false)
    }
    /*
    textField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        // TODO delete this if we don't need to trigger updates manually
        super.documentChanged(event)

        val markup = textField.editor!!.markupModel
        markup.removeAllHighlighters()
        val attributes = TextAttributes().apply {
          // TODO: Theme color
          foregroundColor = Color.BLUE
          backgroundColor = Color.MAGENTA
          errorStripeColor = Color.CYAN
        }
        markup.addRangeHighlighter(0, event.document.textLength / 2, 0, attributes, HighlighterTargetArea.EXACT_RANGE)
      }
    })
     */
    val panel = JPanel(BorderLayout()).apply {
      add(textField, BorderLayout.CENTER)
    }
    val shortcut = KeymapUtil.getShortcutsText(CommonShortcuts.CTRL_ENTER.shortcuts)
    val scaledHeight = JBDimension(0, 100).height
    val popup = (JBPopupFactory.getInstance().createComponentPopupBuilder(panel, textField).apply {
      setAdText("Select up to $MAX_REMOTE_REPOSITORY_COUNT repositories, use $shortcut to finish")
      setCancelOnClickOutside(true)
      setMayBeParent(true)
      setMinSize(Dimension(width, scaledHeight))
      setRequestFocus(true)
      setResizable(true)
    }).createPopup()

    // TODO: The popup "ad text" is gratuitously wrapped.

    val okAction = object : DumbAwareAction() {
      override fun actionPerformed(event: AnActionEvent) {
        unregisterCustomShortcutSet(popup.content)
        popup.closeOk(event.inputEvent)
      }
    }
    okAction.registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, popup.content)

    // TODO: Wire up completion provider.

    return popup
  }
}