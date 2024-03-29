package com.sourcegraph.cody.context.ui

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.RemoteRepoListParams
import org.jetbrains.annotations.NotNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class RemoteRepo(
    val name: String,
)

class RemoteRepoCompletionProvider(private var project: Project) : TextFieldWithAutoCompletionListProvider<String>(listOf()),
    DumbAware {

    // The plain document moves the insertion offset at . or /. This insertion handler makes the repo completion occupy
    // the whole line.
    // TODO: Work out how to disable "insert by pressing ." for this provider/editor/document and remove this.
    // https://stackoverflow.com/questions/71885263/intellij-plugin-completion-exits-on-dot
    val insertHandler =
        InsertHandler<LookupElement> { context, item ->
            val line = context.document.getLineNumber(context.startOffset)
            val start = context.document.getLineStartOffset(line)
            val end = context.document.getLineEndOffset(line)
            context.document.replaceString(start, end, item.lookupString)
        }

    override fun createInsertHandler(item: String): InsertHandler<LookupElement>? {
        return insertHandler
    }

    override fun getAdvertisement(): String? {
        // TODO: L10N
        return "Contact your Sourcegraph admin to add a missing repo."
    }

    override fun getLookupString(s: String): String {
        return s
    }

    override fun acceptChar(c: Char): CharFilter.Result? {
        return CharFilter.Result.ADD_TO_PREFIX
    }

    override fun getPrefix(text: String, offset: Int): String? {
        return text
    }

    override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet {
        // Return all the results. They are already filtered by fuzzysort, we don't want to filter them further.
        result.restartCompletionOnAnyPrefixChange()
        return result
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
        val result = CompletableFuture<List<String>>()
        CodyAgentService.withAgent(project) { agent ->
            try {
                val repos = agent.server.remoteRepoList(RemoteRepoListParams(
                    query = prefix,
                    after = null,
                    first = 250,
                ))
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
