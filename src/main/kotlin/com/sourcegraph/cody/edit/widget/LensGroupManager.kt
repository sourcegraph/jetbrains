package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.sourcegraph.cody.agent.CodyAgentService.Companion.toRange
import com.sourcegraph.cody.agent.protocol.Position
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.edit.CodyInlineEditActionNotifier
import com.sourcegraph.cody.edit.FixupService
import com.sourcegraph.cody.edit.sessions.FixupSession

import com.sourcegraph.cody.edit.sessions.EditsManager

class LensGroupManager(private val session: FixupSession,
                       val project: Project,
                       var editor: Editor,
                       val controller: FixupService)  {
  private lateinit var editsManager: EditsManager
  fun setEditsManager(editsManager: EditsManager) {
    this.editsManager = editsManager
  }

  private val logger = Logger.getInstance(LensWidgetGroup::class.java)

  //A hashmap of lensGroups to list of associated text edit uuids
  private var lensGroupsToEditIds = mutableMapOf<LensWidgetGroup, List<String>>()

  // Tracking which groups are displayed
  private var workingGroupDisplayedBool = false
  private var errorGroupDisplayedBool = false
  private var actionGroupDisplayedBool = false

  fun updateDisplayBools(type : LensGroupType) {
    workingGroupDisplayedBool = false
    errorGroupDisplayedBool = false
    actionGroupDisplayedBool = false
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
        }  }

  fun isWorkingGroupDisplayed() : Boolean {
    return workingGroupDisplayedBool
  }
  fun isErrorGroupDisplayed() : Boolean {
    return errorGroupDisplayedBool
  }
  fun isActionGroupDisplayed() : Boolean {
    return actionGroupDisplayedBool
  }


  fun getLensGroups() : List<LensWidgetGroup> {
    return lensGroupsToEditIds.keys.toList()
  }

  fun removeAllLenses() {
    lensGroupsToEditIds.clear()
  }

  fun getHeaderGroup(): LensWidgetGroup? {
    return lensGroupsToEditIds.keys.firstOrNull()
  }

  fun getNumberOfLensGroups() : Int {
    return lensGroupsToEditIds.size
  }

//  fun getAssociatedEditIds(lensGroup: LensWidgetGroup): List<TextEdit> {
//    var associatedIds =  lensGroupsToEditIds[lensGroup]
//
//    var associatedEdits = mutableListOf<TextEdit>()
//    if (associatedIds != null) {
//      associatedIds.forEach {
//        val associatedEdit = editsManager.getEditById(it)
//        if (associatedEdit != null) {
//          associatedEdits.add(associatedEdit)
//        }
//      }
//    }
//    return associatedEdits
//  }

  fun displayLensGroups(type: LensGroupType, message: String? = null) {
    when (type) {
      LensGroupType.WORKING_GROUP -> displayWorkingGroup()
      LensGroupType.ERROR_GROUP -> displayErrorGroup(message ?: "Edit Failed")
      LensGroupType.ACTION_GROUPS -> displayActionGroups()
    }
  }

  private fun displayWorkingGroup() = runInEdt {
    val group = LensGroupFactory(session).createTaskWorkingGroup()
    session.selectionRange?.let { showLensGroup(group, it.toRange()) }
    updateDisplayBools(LensGroupType.WORKING_GROUP)
    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_WORKING_GROUP)
  }

  fun displayErrorGroup(hoverText: String) = runInEdt {
    session.selectionRange?.let { showLensGroup(LensGroupFactory(session).createErrorGroup(hoverText), it.toRange()) }
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
        // Create a header group for the first edit
        val headerGroup = LensGroupFactory(session).createHeaderGroup()
        addLensGroup(headerGroup, edits.mapNotNull { it.id }) // Heading group maps to all EditIds

        edit.range?.let { showLensGroup(headerGroup, it) }
      } else {
        // Create a block group for subsequent edits
        val blockGroup = LensGroupFactory(session).createBlockGroup(edit.id)
        addLensGroup(blockGroup, listOf(edit.id)) // Block group maps to just the current EditId

        edit.range?.let { showLensGroup(blockGroup, it) }
      }
    }

    updateDisplayBools(LensGroupType.ACTION_GROUPS)

    publishProgress(CodyInlineEditActionNotifier.TOPIC_DISPLAY_ACTION_GROUPS)
  }

  fun updateActionGroups() {
    // Remove all displayed groups (not the lensGroupsToEditIds)(Just the display)
    unrenderAllCodeLenses()

    // Re-display all remaining action groups
    displayActionGroups()
  }

  fun showLensGroup(group: LensWidgetGroup, range: Range) {
//    try {
//      group.let { if (!it.isDisposed.get()) Disposer.dispose(it) }
//    } catch (x: Exception) {
//      logger.warn("Error disposing previous lens group", x)
//    }

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

  fun addLensGroup(lensGroup: LensWidgetGroup, editIds: List<String?>) {
    lensGroupsToEditIds[lensGroup] = editIds as List<String>
  }


  private fun publishProgress(topic: Topic<CodyInlineEditActionNotifier>) {
    ApplicationManager.getApplication().invokeLater {
      project.messageBus.syncPublisher(topic).afterAction()
    }
  }

  fun unrenderAllCodeLenses() {
    lensGroupsToEditIds.forEach { group ->
      var lensGroup = group.key
      try {
        if (!lensGroup.isDisposed.get()) Disposer.dispose(lensGroup)
      } catch (x: Exception) {
        logger.warn("Error disposing lens group", x)
      }
    }
  }

  fun disposeOfAllCodeLenses() {
    if (project.isDisposed) return

    lensGroupsToEditIds.forEach { group ->
      var lensGroup = group.key
      try {
        if (!lensGroup.isDisposed.get()) Disposer.dispose(lensGroup)
      } catch (x: Exception) {
        logger.warn("Error disposing lens group", x)
      }
    }
    lensGroupsToEditIds.clear()

    publishProgress(CodyInlineEditActionNotifier.TOPIC_TASK_FINISHED)
  }

  fun removeLensGroupByEditId(editId: String) {
    lensGroupsToEditIds.forEach { group ->
      var editIds = group.value
      if (editIds.contains(editId)) {
        var lensGroup = group.key
        try {
          if (!lensGroup.isDisposed.get()) Disposer.dispose(lensGroup)
        } catch (x: Exception) {
          logger.warn("Error disposing lens group", x)
        }
        lensGroupsToEditIds.remove(lensGroup)
      }
    }
  }
}
