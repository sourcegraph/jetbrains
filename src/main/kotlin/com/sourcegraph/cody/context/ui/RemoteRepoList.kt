package com.sourcegraph.cody.context.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CheckBoxListListener
import com.intellij.ui.components.JBPanel
import com.sourcegraph.cody.agent.CodyAgentService
import com.sourcegraph.cody.agent.protocol.RemoteRepoListParams
import com.sourcegraph.cody.agent.protocol.RemoteRepoListResponse
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import javax.swing.DefaultListModel
import javax.swing.JCheckBox

data class RemoteRepo(
    val name: String,
)

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