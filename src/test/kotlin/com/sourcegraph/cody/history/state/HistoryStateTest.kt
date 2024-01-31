package com.sourcegraph.cody.history.state

import com.intellij.configurationStore.serialize
import com.sourcegraph.cody.history.state.MessageState.SpeakerState.ASSISTANT
import com.sourcegraph.cody.history.state.MessageState.SpeakerState.HUMAN
import junit.framework.TestCase
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.time.LocalDateTime

class HistoryStateTest : TestCase() {

  fun `test history serialization`() {
    val history = HistoryState().apply {
      chats += ChatState().apply {
        internalId = "0f8b7034-9fa8-488a-a13e-09c52677008a"
        setUpdatedTimeAt(LocalDateTime.parse("1972-01-01T06:00:00"))
        messages += MessageState().apply {
          speaker = HUMAN
          text = "hi"
        }
        messages += MessageState().apply {
          speaker = ASSISTANT
          text = "hello"
        }
      }
    }

    val format = Format.getPrettyFormat().also { it.setLineSeparator("\n") }
    val serialized = XMLOutputter(format).outputString(serialize(history))
    assertEquals("""
      <HistoryState>
        <chats>
          <list>
            <chat>
              <internalId value="0f8b7034-9fa8-488a-a13e-09c52677008a" />
              <messages>
                <list>
                  <message>
                    <speaker value="HUMAN" />
                    <text value="hi" />
                  </message>
                  <message>
                    <speaker value="ASSISTANT" />
                    <text value="hello" />
                  </message>
                </list>
              </messages>
              <updatedAt value="1972-01-01T06:00:00" />
            </chat>
          </list>
        </chats>
      </HistoryState>
    """.trimIndent(), serialized)
  }
}
