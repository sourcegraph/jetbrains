package com.sourcegraph.cody.edit

import com.intellij.util.messages.Topic

/** Pubsub interface shared by all inline edit notifications that accept a FixupSession. */
interface CodyInlineEditActionNotifier {

  // Encapsulates the FixupSession and allows adding new fields without breaking subscribers.
  data class Context(val session: FixupSession)

  fun afterAction(context: Context)

  companion object {
    /** Sent once we have established the selection range, after fetching folding ranges. */
    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_FOLDING_RANGES =
        Topic.create(
            "Sourcegraph Cody: Received folding ranges", CodyInlineEditActionNotifier::class.java)

    /** Sent when the "Cody is working..." lens is displayed during an inline edit. */
    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_DISPLAY_WORKING_GROUP =
        Topic.create(
            "Sourcegraph Cody: Cody working lens shown", CodyInlineEditActionNotifier::class.java)

    /** Sent after a workspace/edit is applied. */
    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_WORKSPACE_EDIT =
        Topic.create(
            "Sourcegraph Cody: Inline Edit completed", CodyInlineEditActionNotifier::class.java)
  }
}
