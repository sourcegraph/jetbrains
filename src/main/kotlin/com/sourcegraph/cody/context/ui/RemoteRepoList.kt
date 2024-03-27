package com.sourcegraph.cody.context.ui

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.RemoteRepoListParams
import com.sourcegraph.cody.agent.protocol.RemoteRepoListResponse
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.DefaultListModel
import javax.swing.JCheckBox

data class RemoteRepo(
    val name: String,
)

class RemoteRepoCompletionProvider(private var project: Project) : TextFieldWithAutoCompletionListProvider<String>(listOf()),
    DumbAware {
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

class RemoteRepoThinger(private val project: Project, private val textField: TextFieldWithAutoCompletion<String>) {
    @Volatile
    var query: String? = null

    // TODO: Edt thread annotations
    fun didUpdateQuery(query: String) {
        this.query = query
        doQuery(query)
    }

    private fun doQuery(query: String) {
        CodyAgentService.withAgent(project) { agent ->
            try {
                val repos = agent.server.remoteRepoList(RemoteRepoListParams(
                    query = query,
                    after = null,
                    first = 100,
                ))
                val repoNames = repos.get().repos.map {
                    it.name
                }
                UIUtil.invokeLaterIfNeeded {
                    if (this.query == query) {
                        textField.setVariants(repoNames)
                    }
                }
            } catch (e: Exception) {
                // TODO: Indicate something went wrong.
            }
        }
    }
}

class RemoteRepoList(private val project: Project) : JBPanel<RemoteRepoList>(BorderLayout()) {
    // JB CheckBoxList uses a concrete list of JCheckBoxes as the data model, shrug.
    val model = object : DefaultListModel<JCheckBox>() {
    }

    val checkListener = CheckBoxListListener { index, value ->
        {
            TODO("Not yet implemented")
        }
    }

    init {
        val checkboxList = CheckBoxList<RemoteRepo>(this.model, this.checkListener)
        this.model.add(0, JCheckBox("github.com/sourcegraph/cody"))
        this.model.add(0, JCheckBox("github.com/sourcegraph/sourcegraph"))

        this.add(checkboxList)

        // TODO: Can I call this on the UI thread?
        val result = CompletableFuture<RemoteRepoListResponse>()
        CodyAgentService.withAgent(project) { agent ->
            try {
                val list = agent.server.remoteRepoList(RemoteRepoListParams(
                    query = null,
                    after = null,
                    first = 10,
                ))
                for (repo in list.get().repos) {
                    // TODO: What thread is this?
                    this.model.add(0, JCheckBox(repo.name))
                }
                result.complete(list.get())
            } catch (e: Exception) {
                result.complete(RemoteRepoListResponse(
                    startIndex = -1,
                    count = 0,
                    repos = emptyList(),
                ))
            }
        }
    }
}