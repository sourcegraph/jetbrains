package com.sourcegraph.cody.chat.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.protocol.ChatMessage
import com.sourcegraph.cody.agent.protocol.Speaker
import com.sourcegraph.cody.attribution.AttributionListener
import com.sourcegraph.cody.attribution.AttributionSearchCommand
import com.sourcegraph.cody.chat.*
import com.sourcegraph.cody.ui.HtmlViewer.createHtmlViewer
import java.awt.Color
import java.util.*
import javax.swing.JEditorPane
import javax.swing.JPanel
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class SingleMessagePanel(
    private val chatMessage: ChatMessage,
    private val project: Project,
    private val parentPanel: JPanel,
    private val gradientWidth: Int,
) : PanelWithGradientBorder(gradientWidth, chatMessage.speaker) {
  private var lastMessagePart: MessagePart? = null

  init {
    val markdownNodes: Node = markdownParser.parse(chatMessage.actualMessage())
    markdownNodes.accept(MessageContentCreatorFromMarkdownNodes(this, htmlRenderer))
  }

  fun getMessageId(): UUID = chatMessage.id

  fun updateContentWith(message: ChatMessage) {
    val markdownNodes = markdownParser.parse(message.actualMessage())
    val lastMarkdownNode = markdownNodes.lastChild
    if (lastMarkdownNode != null && lastMarkdownNode.isCodeBlock()) {
      val (code, language) = lastMarkdownNode.extractCodeAndLanguage()
      addOrUpdateCode(code, language)
    } else {
      val nodesAfterLastCodeBlock = markdownNodes.findNodeAfterLastCodeBlock()
      val renderedHtml = htmlRenderer.render(nodesAfterLastCodeBlock)
      addOrUpdateText(renderedHtml)
    }
  }

  fun addOrUpdateCode(code: String, language: String?) {
    val lastPart = lastMessagePart
    if (lastPart is CodeEditorPart) {
      lastPart.updateCode(project, code, language)
    } else {
      addAsNewCodeComponent(code, language)
    }
  }

  private fun addAsNewCodeComponent(code: String, info: String?) {
    val codeEditorComponent =
        CodeEditorFactory(project, parentPanel, gradientWidth).createCodeEditor(code, info)
    this.lastMessagePart = codeEditorComponent
    add(codeEditorComponent.component)
  }

  fun addOrUpdateText(text: String) {
    val lastPart = lastMessagePart
    if (lastPart is CodeEditorPart) {
      val updateInUiThread = AttributionListener { response ->
        ApplicationManager.getApplication().invokeLater {
          lastPart.attributionListener.updateAttribution(response)
        }
      }
      // TODO: Also call attribution when code snippet is the last piece of chat UI.
      AttributionSearchCommand(project)
          .onSnippetFinished(lastPart.text, chatMessage.id, updateInUiThread)
    }
    if (lastPart is TextPart) {
      lastPart.updateText(text)
    } else {
      addAsNewTextComponent(text)
    }
  }

  private fun addAsNewTextComponent(renderedHtml: String) {
    val textPane: JEditorPane = createHtmlViewer(getInlineCodeBackgroundColor(chatMessage.speaker))
    SwingHelper.setHtml(textPane, renderedHtml, null)
    val textEditorComponent = TextPart(textPane)
    this.lastMessagePart = textEditorComponent
    add(textEditorComponent.component)
  }

  private fun getInlineCodeBackgroundColor(speaker: Speaker): Color {
    return if (speaker == Speaker.ASSISTANT) ColorUtil.darker(UIUtil.getPanelBackground(), 3)
    else ColorUtil.brighter(UIUtil.getPanelBackground(), 3)
  }

  companion object {
    private val extensions = listOf(TablesExtension.create())

    private val markdownParser = Parser.builder().extensions(extensions).build()
    private val htmlRenderer =
        HtmlRenderer.builder().softbreak("<br />").extensions(extensions).build()
  }
}
