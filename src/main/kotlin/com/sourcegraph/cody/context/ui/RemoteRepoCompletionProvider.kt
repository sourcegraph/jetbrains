package com.sourcegraph.cody.context.ui

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.CharFilter
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

    override fun getAdvertisement(): String? {
        // TODO: L10N
        return "Contact your Sourcegraph admin to add a missing repo."
    }

    @NotNull
    override fun getLookupString(@NotNull s: String): String {
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
        return result
    }

    override fun getItems(
        prefix: String?,
        cached: Boolean,
        parameters: CompletionParameters?
    ): MutableCollection<String> {
        if (cached) {
            return mutableListOf()
        }
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
