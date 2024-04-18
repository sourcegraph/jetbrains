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

    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_DISPLAY_ACCEPT_GROUP =
        Topic.create(
            "Sourcegraph Cody: Accept lens shown", CodyInlineEditActionNotifier::class.java)

    /** Sent when the user selects the Undo action and the edits are discarded. */
    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_PERFORM_UNDO =
        Topic.create("Sourcegraph Cody: Undo Inline Edit", CodyInlineEditActionNotifier::class.java)

    /** Sent when the user performs the Accept action and the edits are kept. */
    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_PERFORM_ACCEPT =
        Topic.create(
            "Sourcegraph Cody: Accept Inline Edit", CodyInlineEditActionNotifier::class.java)

    /** Sent after a workspace/edit is applied. */
    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_WORKSPACE_EDIT =
        Topic.create(
            "Sourcegraph Cody: Inline Edit completed", CodyInlineEditActionNotifier::class.java)
  }
}
