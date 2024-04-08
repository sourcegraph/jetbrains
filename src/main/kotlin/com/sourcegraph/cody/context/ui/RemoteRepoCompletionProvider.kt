package com.sourcegraph.cody.context.ui

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.getThrowableText
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.RemoteRepoListParams
import com.sourcegraph.cody.context.RemoteRepoSearcher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.Icon


data class RemoteRepo(
    val name: String,
)

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
                    val element = parameters.originalPosition
                    val query =
                        if (element?.elementType == RemoteRepoTokenType.REPO) {
                            // TODO: Should we use the original element like this?
                            // IntelliJ helpfully? appends "dummy" text, like IntellijIdeaRulezzz which we must now strip.
                            element.text // element.text.substring(0, element.text.length - CompletionInitializationContext.DUMMY_IDENTIFIER.length)
                        } else {
                            null
                        }
                    val prefixedResult = if (query != null) { result.withPrefixMatcher(query) } else { result }
                    prefixedResult.restartCompletionOnAnyPrefixChange()
                    try {
                        // TODO: Hang the thread here, until the agent notifies us that all the repositories are fetched.
                        val repos = searcher.search(query).get(15, TimeUnit.SECONDS)
                        for (repo in repos) {
                            prefixedResult.addElement(LookupElementBuilder.create(repo).withIcon(getIcon(repo)))
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

    private fun getIcon(item: String): Icon? {
        return when {
            item.startsWith("github.com/") -> Icons.RepoHostGitHub
            item.startsWith("gitlab.com/") -> Icons.RepoHostGitlab
            item.startsWith("bitbucket.org/") -> Icons.RepoHostBitbucket
            else -> Icons.RepoHostGeneric
        }
    }
}

class RemoteRepoCompletionProvider(private var project: Project) : TextFieldWithAutoCompletionListProvider<String>(listOf()),
    DumbAware {

    override fun getAdvertisement(): String? {
        // TODO: L10N
        return "Contact your Sourcegraph admin to add a missing repo."
    }

    override fun getLookupString(s: String): String {
        return s
    }

    override fun acceptChar(c: Char): CharFilter.Result? {
        // Tab, enter are handled by the autocomplete popup.
        return if (c == ' ') { CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP } else { CharFilter.Result.ADD_TO_PREFIX }
    }

    override fun getPrefix(text: String, offset: Int): String? {
        // Claim responsibility for the whole prefix.
        val separatorChars = charArrayOf(' ', '\n')
        val start = text.subSequence(0, offset).lastIndexOfAny(separatorChars) + 1 // skip the separator itself
        var end = text.indexOfAny(separatorChars, offset)
        if (end == -1) {
            end = text.length
        }
        val prefix = text.substring(start, end)
        println("getPrefix: $text offset: $offset prefix: $prefix")
        return prefix
    }

    override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet {
        // Extension-side fuzzysort is already sorting and trimming completions, so delegate to it whenever the prefix
        // changes.
        result.restartCompletionOnAnyPrefixChange()
        return result.withPrefixMatcher(prefix)
    }

    override fun getIcon(item: String): Icon? {
        return when {
            item.startsWith("github.com/") -> Icons.RepoHostGitHub
            item.startsWith("gitlab.com/") -> Icons.RepoHostGitlab
            item.startsWith("bitbucket.org/") -> Icons.RepoHostBitbucket
            else -> Icons.RepoHostGeneric
        }
    }

    override fun getItems(
        prefix: String?,
        cached: Boolean,
        parameters: CompletionParameters?
    ): MutableCollection<String> {
        if (cached) {
            // TODO: When loading is done, treat completions as "cached" items.
            return mutableListOf()
        }
        // TODO: Hang the thread here, until the agent notifies us that all the repositories are fetched.
        while (true) {
            val result = CompletableFuture<List<String>>()
            CodyAgentService.withAgent(project) { agent ->
                agent.client
                try {
                    val repos = agent.server.remoteRepoList(
                        RemoteRepoListParams(
                            query = prefix,
                            after = null,
                            first = 250,
                        )
                    )
                    val repoNames = repos.get().repos.map {
                        it.name
                    }
                    result.complete(repoNames)
                } catch (e: Exception) {
                    // TODO: Indicate something went wrong.
                }
            }
            // TODO: Handle failure
            return result.get(15, TimeUnit.SECONDS).toMutableList()
        }
    }
}
