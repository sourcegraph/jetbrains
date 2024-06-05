package com.sourcegraph.cody.edit

import com.intellij.util.messages.Topic
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.edit.sessions.FixupSession

/** Pubsub interface shared by all inline edit notifications that accept a FixupSession. */
interface CodyInlineEditActionNotifier {

  // Encapsulates the FixupSession and allows adding new fields without breaking subscribers.
  data class Context(val session: FixupSession, val selectionRange: Range? = null)

  fun afterAction(context: Context)

  companion object {
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

    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_DISPLAY_ERROR_GROUP =
        Topic.create("Sourcegraph Cody: Error lens shown", CodyInlineEditActionNotifier::class.java)

    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_PERFORM_UNDO =
        Topic.create("Sourcegraph Cody: Undo Inline Edit", CodyInlineEditActionNotifier::class.java)

    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_PERFORM_ACCEPT =
        Topic.create(
            "Sourcegraph Cody: Accept Inline Edit", CodyInlineEditActionNotifier::class.java)

    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_PERFORM_CANCEL =
        Topic.create(
            "Sourcegraph Cody: Cancel Inline Edit", CodyInlineEditActionNotifier::class.java)

    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_WORKSPACE_EDIT =
        Topic.create(
            "Sourcegraph Cody: workspace/edit completed", CodyInlineEditActionNotifier::class.java)

    @JvmStatic
    @Topic.ProjectLevel
    val TOPIC_TEXT_DOCUMENT_EDIT =
        Topic.create(
            "Sourcegraph Cody: textDocument/edit completed",
            CodyInlineEditActionNotifier::class.java)
  }
}
