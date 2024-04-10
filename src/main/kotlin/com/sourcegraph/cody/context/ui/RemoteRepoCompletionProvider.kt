package com.sourcegraph.cody.context.ui

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.elementType
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.util.ProcessingContext
import com.jetbrains.rd.util.TimeoutException
import com.jetbrains.rd.util.getThrowableText
import com.sourcegraph.Icons
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.RemoteRepoListParams
import com.sourcegraph.cody.context.RemoteRepoSearcher
import kotlinx.coroutines.isActive
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
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