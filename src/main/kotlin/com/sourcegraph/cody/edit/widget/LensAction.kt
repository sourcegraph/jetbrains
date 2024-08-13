package com.sourcegraph.cody.edit.widget

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.sourcegraph.cody.agent.protocol.Range
import com.sourcegraph.cody.edit.EditUtil
import com.sourcegraph.cody.edit.sessions.FixupSession
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.annotations.VisibleForTesting

class LensAction(
    val group: LensWidgetGroup,
    private val text: String,
    @VisibleForTesting val actionId: String,
    private val editId: String? = null
    //private val range: Range? = null
) : LensWidget(group) {

  object DataKeys {
    val EDIT_ID_DATA_KEY: DataKey<String> = DataKey.create("com.sourcegraph.cody.edit.actions.EditId")
    //val RANGE_DATA_KEY: DataKey<Range> = DataKey.create("com.sourcegraph.cody.edit.actions.Range")
  }
  private val highlight =
      LabelHighlight(
          when (actionId) {
            FixupSession.ACTION_ACCEPT -> acceptColor
            FixupSession.ACTION_ACCEPT_ALL -> acceptColor
            FixupSession.ACTION_REJECT -> rejectColor
            FixupSession.ACTION_REJECT_ALL -> rejectColor
            else -> actionColor
          })

  override fun calcWidthInPixels(fontMetrics: FontMetrics): Int {
    return fontMetrics.stringWidth(text) + (2 * SIDE_MARGIN)
  }

  override fun calcHeightInPixels(fontMetrics: FontMetrics): Int = fontMetrics.height

  override fun paint(g: Graphics2D, x: Float, y: Float) {
    val originalFont = g.font
    val originalColor = g.color
    try {
      g.background = EditUtil.getEnhancedThemeColor("Panel.background")

      val metrics = g.fontMetrics
      val width = calcWidthInPixels(metrics) - 4
      val textHeight = metrics.height

      highlight.drawHighlight(g, x, y + 1, width, textHeight - 2)

      if (mouseInBounds) {
        g.font = g.font.deriveFont(Font.BOLD).deriveFont(g.font.size * 0.85f)
        g.color = UIUtil.shade(JBColor(0xFFFFFF, 0xFFFFFF), 1.0, 0.95)
      } else {
        g.font = g.font.deriveFont(Font.BOLD).deriveFont(g.font.size * 0.85f)
        g.color = UIUtil.shade(JBColor(0xFFFFFF, 0xFFFFFF), 1.0, 0.9)
      }

      g.drawString(text, x + SIDE_MARGIN, y + g.fontMetrics.ascent + 1)

      lastPaintedBounds =
          Rectangle2D.Float(x, y - metrics.ascent, width.toFloat(), textHeight.toFloat())
    } finally {
      g.font = originalFont
      g.color = originalColor
    }
  }

  override fun onClick(e: EditorMouseEvent): Boolean {
    triggerAction(actionId, e.editor, e.mouseEvent)
    return true
  }

  private fun triggerAction(actionId: String, editor: Editor, mouseEvent: MouseEvent) {
    lastLensActionPerformed.set(actionId)
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
        DataKeys.EDIT_ID_DATA_KEY.name -> editId
        //DataKeys.RANGE_DATA_KEY.name -> range
        else -> null
      }
    }
  }

  override fun toString(): String {
    return "LensAction(text=$text)"
  }

  companion object {
    const val SIDE_MARGIN = 9

    val actionColor = JBColor(0x4C4D54, 0x393B40)
    private val acceptColor = JBColor(0x369650, 0x388119)
    private val rejectColor = JBColor(0xCC3645, 0x7B282C)

    private val lastLensActionPerformed: AtomicReference<String?> = AtomicReference(null)

    fun wasLastLensActionAnAccept(): Boolean {
      return lastLensActionPerformed.get() == FixupSession.ACTION_ACCEPT
    }
  }
}
