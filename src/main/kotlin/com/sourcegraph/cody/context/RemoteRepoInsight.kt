package com.sourcegraph.cody.context

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.getThrowableText
import com.sourcegraph.Icons
import com.sourcegraph.cody.context.ui.MAX_REMOTE_REPOSITORY_COUNT
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

data class RemoteRepo(
    val name: String
) {
    val displayName: String
        get() = name.substring(name.indexOf('/') + 1) // Note, works for names without / => full name.

    val icon: Icon?
        get() = when {
            name.startsWith("github.com/") -> Icons.RepoHostGitHub
            name.startsWith("gitlab.com/") -> Icons.RepoHostGitlab
            name.startsWith("bitbucket.org/") -> Icons.RepoHostBitbucket
            else -> Icons.RepoHostGeneric
        }
}

val RemoteRepoLanguage = object : Language("SourcegraphRemoteRepoList") {}

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
        return "Sourcegraph Remote Repo File"
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
        // TODO: Messages/tooltips are not appearing on hover, but they *do* appear if the editor/popup is not focused.
        // Debug how popups interact with tooltips and re-enable tooltips.
        when (element.elementType) {
            RemoteRepoTokenType.REPO -> {
                val name = element.text
                val service = RemoteRepoSearcher.getInstance(element.project)
                runBlockingCancellable {
                    if (!service.has(name)) {
                        blockingContext {
                            // TODO: L10N
                            holder.newAnnotation(HighlightSeverity.ERROR, "Repository not found").tooltip("Repository not found").range(element).create()
                        }
                    }
                }
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

// TODO: Make this DumbAware to provide completions earlier. It means the RemoteRepoSearcher cannot be a service,
// however.
class RemoteRepoCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters?>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val searcher = RemoteRepoSearcher.getInstance(parameters.position.project)
                    // We use original position, if present, because it does not have the "helpful" dummy text
                    // "IntellijIdeaRulezzz". Because we do a fuzzy match, we use the whole element as the query.
                    val element = parameters.originalPosition
                    val query =
                        if (element?.elementType == RemoteRepoTokenType.REPO) {
                            element.text
                        } else {
                            null  // Return all repos
                        }
                    // Update the prefix to the whole query to get accurate highlighting.
                    val prefixedResult = if (query != null) { result.withPrefixMatcher(query) } else { result }
                    prefixedResult.restartCompletionOnAnyPrefixChange()
                    try {
                        runBlockingCancellable {
                            for (repos in searcher.search(query)) {
                                blockingContext { // addElement uses ProgressManager.checkCancelled
                                    for (repo in repos) {
                                        prefixedResult.addElement(
                                            LookupElementBuilder.create(repo).withIcon(RemoteRepo(repo).icon)
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        prefixedResult.addLookupAdvertisement(e.getThrowableText())
                    }
                }
            }
        )
    }

    override fun handleEmptyLookup(parameters: CompletionParameters, editor: Editor?): String? {
        // TODO: L10N
        return "Contact your Sourcegraph admin to add a missing repo."
    }
}