package com.sourcegraph.cody.history.state

import junit.framework.TestCase

class HistoryStateTest : TestCase() {

  fun `test deserialization has no breaking changes between versions`() {
    // todo persistent xml contains internal class names
    // todo add test to ensure that tags like <HistoryChatState> are never changed
    /*
    <application>
      <component name="com.sourcegraph.cody.history.HistoryService">
        <option name="activeChatId" value="d32f3e93-e7a9-4a55-869d-2d7d54ac8fa4" />
        <option name="chats">
          <list>
            <HistoryChatState>
              <option name="id" value="d32f3e93-e7a9-4a55-869d-2d7d54ac8fa4" />
              <option name="lastUpdated" value="2023-12-11T02:20:31.206975" />
              <option name="messages">
                <list>
                  <HistoryChatMessageState>
                    <option name="speaker" value="ASSISTANT" />
                    <option name="text" value="Hello! I'm Cody. I can write code and answer questions for you. See [Cody documentation](https://docs.sourcegraph.com/cody) for help and tips." />
                    <option name="type" value="CHAT_MESSAGE" />
                  </HistoryChatMessageState>
                </list>
              </option>
            </HistoryChatState>
          </list>
        </option>
      </component>
    </application>
     */
    // todo OR remove this problem? maybe we can omit tags like this and rely on structur
  }
}
