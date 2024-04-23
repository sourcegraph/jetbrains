package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.sourcegraph.cody.edit.EditCommandPrompt
import com.sourcegraph.cody.edit.sessions.FixupSession
import com.sourcegraph.config.ThemeUtil
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.geom.Rectangle2D
import javax.swing.UIManager

class LensAction(
    val group: LensWidgetGroup,
    labelText: String,
    val command: String,
    private val actionId: String
) : LensWidget(group) {

  private val underline = mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON)

  private val text = " $labelText "

  // TODO: Put in resources
  val actionColor = Color(44, 45, 50)
  val acceptColor = Color(37, 92, 53)
  val undoColor = Color(114, 38, 38)

  private val highlight =
      LabelHighlight(
          when (actionId) {
            FixupSession.ACTION_ACCEPT -> acceptColor
            FixupSession.ACTION_UNDO -> undoColor
            else -> actionColor
          })

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int = fontMetrics.stringWidth(text)

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    val originalFont = g.font
    val originalColor = g.color
    try {
      g.background =
          UIManager.getColor("Panel.background").run {
            if (ThemeUtil.isDarkTheme()) darker() else brighter()
          }
      val metrics = g.fontMetrics
      val textWidth = metrics.stringWidth(text)
      val textHeight = metrics.height
      highlight.drawHighlight(g, x, y, textWidth, textHeight)

      val linkColor = UIManager.getColor("Link.hoverForeground")
      if (mouseInBounds) {
        g.font = g.font.deriveFont(underline)
        g.color = linkColor
      } else {
        g.font = g.font.deriveFont(Font.PLAIN)
        g.color = EditCommandPrompt.boldLabelColor()
      }
      g.drawString(text, x, y + g.fontMetrics.ascent)

      lastPaintedBounds =
          Rectangle2D.Float(x, y - metrics.ascent, textWidth.toFloat(), textHeight.toFloat())
    } finally {
      // Other lenses are using the same Graphics2D.
      g.font = originalFont
      g.color = originalColor
    }
  }

  override fun onClick(e: EditorMouseEvent): Boolean {
    triggerAction(actionId, e.editor, e.mouseEvent)
    return true
  }

  override fun onMouseEnter(e: EditorMouseEvent) {
    mouseInBounds = true
    showTooltip(EditCommandPrompt.getShortcutText(actionId) ?: return, e.mouseEvent)
  }

  private fun triggerAction(actionId: String, editor: Editor, mouseEvent: MouseEvent) {
    val action = ActionManager.getInstance().getAction(actionId)
    if (action != null) {
      val dataContext = createDataContext(editor, mouseEvent)
      val actionEvent =
          AnActionEvent(
              null,
              dataContext,
              "",
              action.templatePresentation.clone(),
              ActionManager.getInstance(),
              0)
      action.actionPerformed(actionEvent)
    }
  }

  private fun createDataContext(editor: Editor, mouseEvent: MouseEvent): DataContext {
    return DataContext { dataId ->
      when (dataId) {
        PlatformDataKeys.CONTEXT_COMPONENT.name -> mouseEvent.component
        PlatformDataKeys.EDITOR.name -> editor
        PlatformDataKeys.PROJECT.name -> editor.project
        else -> null
      }
    }
  }

  override fun toString(): String {
    return "LensAction(text=$text)"
  }
}
