package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.sourcegraph.cody.agent.CodyAgentService.Companion.toRange
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.edit.sessions.FixupSession

import com.sourcegraph.cody.edit.sessions.EditsManager

class LensGroupManager(private val session: FixupSession,
                       val project: Project,
                       var editor: Editor,
                       val controller: FixupService)  {
  private val logger = Logger.getInstance(LensWidgetGroup::class.java)
  private lateinit var editsManager: EditsManager
  //A hashmap of lensGroups to list of associated text edit uuids
  private var lensGroupsToEditIds = mutableMapOf<LensWidgetGroup, List<String>>()

  // ** Get/Set **
  fun setEditsManager(editsManager: EditsManager) {
    this.editsManager = editsManager
  }

  private fun addLensGroup(lensGroup: LensWidgetGroup, editIds: List<String?>) {
    val nonNullEditIds = editIds.filterNotNull()
    lensGroupsToEditIds[lensGroup] = nonNullEditIds
  }
  fun getNumberOfLensGroups() : Int {
    return lensGroupsToEditIds.size
  }

  fun getLensGroupByEditId(editId: String): LensWidgetGroup? {
    for ((lensGroup, editIds) in lensGroupsToEditIds) {
      if (editIds.contains(editId)) {
        return lensGroup
      }
    }
    return null
  }

  fun getLensGroups() : List<LensWidgetGroup> {
    return lensGroupsToEditIds.keys.toList()
  }

  fun clearAllLenses() {
    lensGroupsToEditIds.clear()
  }

  // ** Display **

  fun displayLensGroups(type: LensGroupType, message: String? = null) {
    disposeAllCodeLenses() // Dispose of any existing code lenses
    when (type) {
      LensGroupType.WORKING_GROUP -> displayWorkingGroup()
      LensGroupType.ERROR_GROUP -> displayErrorGroup(message ?: "Edit Failed")
      LensGroupType.ACTION_GROUPS -> displayActionGroups()
      else -> {
        logger.error("Invalid lens group type passed in: $type")
      }
    }
  }

  private fun displayWorkingGroup() = runInEdt {
    val group = LensGroupFactory(session).createTaskWorkingGroup()
    session.selectionRange?.let { showLensGroup(group, it.toRange()) }
    addLensGroup(group, emptyList())
    updateDisplayBools(LensGroupType.WORKING_GROUP)
//    session.postAcceptActions()
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
  }

  fun displayErrorGroup(hoverText: String) = runInEdt {
    val group = LensGroupFactory(session).createErrorGroup(hoverText)
    session.selectionRange?.let { showLensGroup(group, it.toRange()) }
    addLensGroup(group, emptyList())
    updateDisplayBools(LensGroupType.ERROR_GROUP)
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ERROR_GROUP)
  }

  @RequiresEdt
  private fun displayActionGroups() {
    var edits = editsManager.edits
    if (edits.isEmpty()) {
      logger.warn("No edits to display")
      return
    }

    // Loop through each edit
    edits.forEachIndexed { index, edit ->
      if (index == 0) {
        displayHeaderGroup()
      }
      edit.id?.let { displayBlockGroup(it) }
    }

    updateDisplayBools(LensGroupType.ACTION_GROUPS)
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ACTION_GROUPS)
  }

  private fun displayHeaderGroup() = runInEdt {
    val group = LensGroupFactory(session).createHeaderGroup()
    session.selectionRange?.let { showLensGroup(group, it.toRange()) }
    val allEditIds = editsManager.getAllEditIds()
    addLensGroup(group, allEditIds)
    updateDisplayBools(LensGroupType.ACTION_GROUPS)
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ACTION_GROUPS)
  }

  private fun displayBlockGroup(editId: String) = runInEdt {
    val group = LensGroupFactory(session).createBlockGroup(editId)
    val edit = editsManager.getEditById(editId)
    val position = edit?.position
    if (position != null) {
      showLensGroup(group, Range(position, position))
    } else {
      logger.warn("Edit with id $editId not found")
    }
    addLensGroup(group, listOf(editId))
    updateDisplayBools(LensGroupType.ACTION_GROUPS)
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ACTION_GROUPS)
  }

  fun showLensGroup(group: LensWidgetGroup, range: Range) {
    val future = group.show(range)

    // Make sure the lens is visible.
    ApplicationManager.getApplication().invokeLater {
      if (!editor.isDisposed) {
        val logicalPosition = range.start.toLogicalPosition(editor.document)
        editor.scrollingModel.scrollTo(logicalPosition, ScrollType.CENTER)
      }
    }
    if (!ApplicationManager.getApplication().isDispatchThread) { // integration test
      future.get()
    }

    controller.notifySessionStateChanged()
  }

  // ** Display Tracking **
  private var workingGroupDisplayedBool = false
  private var errorGroupDisplayedBool = false
  private var actionGroupDisplayedBool = false

  fun updateDisplayBools(type : LensGroupType) {
    when (type) {
      LensGroupType.WORKING_GROUP -> {
        workingGroupDisplayedBool = true
        errorGroupDisplayedBool = false
        actionGroupDisplayedBool = false
      }
      LensGroupType.ERROR_GROUP -> {
        errorGroupDisplayedBool = true
        workingGroupDisplayedBool = false
        actionGroupDisplayedBool = false
      }
      LensGroupType.ACTION_GROUPS -> {
        actionGroupDisplayedBool = true
        errorGroupDisplayedBool = false
        workingGroupDisplayedBool = false
      }
      else -> {
        actionGroupDisplayedBool = false
        errorGroupDisplayedBool = false
        workingGroupDisplayedBool = false
      }
    }
  }

  fun isWorkingGroupDisplayed() : Boolean {
    return workingGroupDisplayedBool
  }

  fun isErrorGroupDisplayed() : Boolean {
    return errorGroupDisplayedBool
  }

  fun isActionGroupDisplayed() : Boolean {
    return actionGroupDisplayedBool
  }


  //ToDo: probably should only implement this in FixupSession (and only call from FixupSession)
  private fun publishProgress(topic: Topic<CodyInlineEditActionNotifier>) {
    ApplicationManager.getApplication().invokeLater {
      project.messageBus.syncPublisher(topic).afterAction()
    }
  }

  // ** Dispose **

  fun disposeCodeLensByEditId(editId: String) {
    val group = this.getLensGroupByEditId(editId)
    lensGroupsToEditIds.remove(group)
    if (group != null) {
      executeDisposeOfCodeLens(group)
    }
  }

  fun disposeAllCodeLenses() = runInEdt {
    for (lensGroup in this.getLensGroups()) {
      executeDisposeOfCodeLens(lensGroup)
    }
    updateDisplayBools(LensGroupType.NONE)
    publishProgress(CodyInlineEditActionNotifier.TOPIC_TASK_FINISHED)
  }

  fun executeDisposeOfCodeLens(group: LensWidgetGroup) = runInEdt {
    try {
      group.dispose()
    } catch (x: Exception) {
      logger.warn("Error disposing lens group", x)
    }
  }
}
