package ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import junit.framework.TestCase

class ChatTest : TestCase() {

    fun `test send chat message and wait for response`() {
        val robot = RemoteRobot("http://127.0.0.1:8082")
        val toolbar = robot.find<ContainerFixture>(byXpath("//div[@tooltiptext='Cody']"))
        toolbar.click()
        println()
        val toolbarChat = robot.find<ContainerFixture>(byXpath("//div[@class='TabContainer']/div[@text='Chat']"))
        toolbarChat.click()
        val textArea = robot.find<ContainerFixture>(byXpath("//div[@class='RoundedJBTextArea']"))
        textArea.click()
        textArea.keyboard {
            enterText("Hello, Cody!")
        }
        val button = robot.find<ContainerFixture>(byXpath("//div[@text='Send']"))
        button.click()
        robot.apply {
            waitFor {
                val messages = robot.findAll<ContainerFixture>(byXpath("//div[@class='MessagePanel']"))
                messages.size > 2
            }
        }
    }

}
