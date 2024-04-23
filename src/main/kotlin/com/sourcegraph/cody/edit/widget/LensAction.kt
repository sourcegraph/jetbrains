package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.sourcegraph.cody.edit.EditCommandPrompt
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.geom.Rectangle2D

class LensAction(
    val group: LensWidgetGroup,
    private val text: String,
    val command: String,
    private val actionId: String,
) : LensWidget(group) {

  private val underline = mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON)

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int = fontMetrics.stringWidth(text)

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun paint(g: Graphics2D, targetRegion: Rectangle2D, x: Float, y: Float) {
    val originalFont = g.font
    val originalColor = g.color
    try {
      val metrics = g.fontMetrics
      val textWidth = metrics.stringWidth(text)
      val textHeight = metrics.height
      // val rectBounds = Rectangle2D(targetRegion.x - metrics.stringWidth(" ", targetRegion.y))
      group.drawBackgroundRectangle(g, targetRegion, this, x)

      if (mouseInBounds) {
        g.font = g.font.deriveFont(underline)
        g.color = JBColor.BLUE // TODO: use theme link rollover color
      } else {
        g.font = g.font.deriveFont(Font.PLAIN)
        g.color = JBColor(Gray._240, Gray._240)
      }
      if (mouseInBounds) g.color = JBColor.BLUE // TODO: use theme link rollover color
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
    showTooltip(EditCommandPrompt.getShortcutText(actionId), e.mouseEvent)
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
