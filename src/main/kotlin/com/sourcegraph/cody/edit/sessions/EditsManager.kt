package com.sourcegraph.cody.edit.sessions

import com.intellij.openapi.diagnostic.Logger
import com.sourcegraph.cody.agent.protocol.TextEdit
import com.sourcegraph.cody.edit.sessions.FixupSession
import com.sourcegraph.cody.edit.widget.LensWidgetGroup
import com.sourcegraph.cody.edit.widget.LensGroupManager
import java.util.UUID


class EditsManager() {

  private lateinit var lensGroupManager: LensGroupManager

  fun setLensGroupManager(lensGroupManager: LensGroupManager) {
    this.lensGroupManager = lensGroupManager
  }

  private val logger = Logger.getInstance(LensWidgetGroup::class.java)
  var edits: List<TextEdit> = emptyList()

  fun initEdits(edits: List<TextEdit>) {
    // Iterate through each edit, assign a UUID, and add it to the list of edits
    this.edits = edits.map {
      it.copy(id = it.id ?: UUID.randomUUID().toString())
    }
  }

  fun getAllEditIds(): List<String> {
    return edits.mapNotNull { it.id }
  }

  fun getEditById(id: String): TextEdit? {
    val edit = edits.find { it.id == id }
    if (edit == null) {
      logger.warn("Edit with ID: $id not found")
    } else {
      logger.info("Found edit with ID: ${edit.id}")
    }
    return edit  }

  fun getFirstEdit(): TextEdit? {
    // Get edit with the lowest position
    return edits
      .filter { it.position != null }
      .minByOrNull { it.position!!.line }
  }

  fun removeEdit(editId: String){
    edits = edits.filter { it.id != editId }
  }

  fun clearAllEdits() {
    edits = emptyList()
  }

  fun repositionEdits() {
    // Sort edits by position descending
    val sortedEdits = edits.sortedByDescending { it.position?.line }

    // For each edit, calculate the sum of ranges of edits with higher positions
    sortedEdits.forEachIndexed { index, edit ->
      val higherEdits = sortedEdits.subList(0, index)
      val sumOfRanges = higherEdits.sumOf { it.range?.let {
        range -> range.end.line - range.start.line } ?: 0
      }.toInt()

      // Add 1 line for each code lens
      val amount = sumOfRanges + lensGroupManager.getNumberOfLensGroups()

      // Reposition the edit
      repositionEdit(edit.id ?: return@forEachIndexed, amount)
    }
  }

  //Shifts the edits position and range by the amount specified
  fun repositionEdit(editId: String, amount: Int) {
    var edit = getEditById(editId)
    if (edit != null) {
      edit.range?.let { range ->
        range.start.line += amount
        range.end.line += amount
      }

      edit.position?.let { position ->
        position.line += amount
      }
    } else{
      logger.warn("Failed to find edit with id: $editId")
    }
  }


}