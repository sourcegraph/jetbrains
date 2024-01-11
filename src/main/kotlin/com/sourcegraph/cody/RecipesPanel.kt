package com.sourcegraph.cody

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.sourcegraph.cody.agent.CodyAgent
import com.sourcegraph.cody.agent.CodyAgentManager
import com.sourcegraph.cody.agent.protocol.RecipeInfo
import com.sourcegraph.cody.autocomplete.CodyEditorFactoryListener
import com.sourcegraph.telemetry.GraphQlLogger
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import java.util.stream.Collectors
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.plaf.ButtonUI

class RecipesPanel(
    val project: Project,
    @RequiresEdt val sendMessage: (message: String, recipeId: String) -> Unit
) : JBPanelWithEmptyText(GridLayout(0, 1)) {
  private val logger = Logger.getInstance(RecipesPanel::class.java)

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }

  @RequiresEdt
  fun refreshAndFetch() {
    removeAll()
    emptyText.text = "Loading commands..."
    revalidate()
    repaint()

    ApplicationManager.getApplication().executeOnPooledThread { loadCommands() }
  }

  @RequiresEdt
  fun enableRecipes() {
    components.filterIsInstance<JButton>().forEach {
      it.isEnabled = true
      it.toolTipText = null
    }
  }

  @RequiresEdt
  fun disableRecipes() {
    components.filterIsInstance<JButton>().forEach {
      it.isEnabled = false
      it.toolTipText = "Message generation in progress..."
    }
  }

  @RequiresEdt
  fun setRecipesPanelError() {
    emptyText.clear()
    emptyText.appendLine("Error fetching commands. Check your connection.")
    emptyText.appendLine("If the problem persists, please contact support.")
    emptyText.appendLine(
        "Retry",
        SimpleTextAttributes(
            SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.Foreground.ENABLED)) {
          refreshAndFetch()
        }
  }

  @RequiresBackgroundThread
  private fun loadCommands() {
    CodyAgentManager.tryRestartingAgentIfNotRunning(project)
    CodyAgent.getInitializedServer(project).thenAccept { server ->
      try {
        server.recipesList().thenAccept { recipes: List<RecipeInfo> ->
          ApplicationManager.getApplication().invokeLater { updateUIWithRecipeList(recipes) }
        }
      } catch (e: Exception) {
        logger.warn("Error fetching commands from agent", e)
        ApplicationManager.getApplication().invokeLater { setRecipesPanelError() }
      }
    }
  }

  @RequiresEdt
  private fun updateUIWithRecipeList(recipes: List<RecipeInfo>) {
    // we don't want to display recipes with ID "chat-question" and "code-question"
    val excludedRecipeIds: List<String?> =
        listOf("chat-question", "code-question", "translate-to-language")
    val recipesToDisplay =
        recipes
            .stream()
            .filter { recipe: RecipeInfo -> !excludedRecipeIds.contains(recipe.id) }
            .collect(Collectors.toList())
    fillRecipesPanel(recipesToDisplay)
    fillContextMenu(recipesToDisplay)
  }

  @RequiresEdt
  private fun fillRecipesPanel(recipes: List<RecipeInfo>) {
    removeAll()

    // Loop on recipes and add a button for each item
    for (recipe in recipes) {
      val recipeButton = createRecipeButton(recipe.title)
      recipeButton.addActionListener {
        val editorManager = FileEditorManager.getInstance(project)
        CodyEditorFactoryListener.Util.informAgentAboutEditorChange(
            editorManager.selectedTextEditor)
        GraphQlLogger.logCodyEvent(project, "recipe:" + recipe.id, "clicked")
        sendMessage(recipe.title, recipe.id)
      }
      add(recipeButton)
    }
  }

  @RequiresEdt
  private fun fillContextMenu(recipes: List<RecipeInfo>) {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("CodyEditorActions") as DefaultActionGroup

    // Loop on recipes and create an action for each new item
    for (recipe in recipes) {
      val actionId = "cody.recipe." + recipe.id
      val existingAction = actionManager.getAction(actionId)
      if (existingAction != null) {
        continue
      }
      val action: DumbAwareAction =
          object : DumbAwareAction(recipe.title) {
            override fun actionPerformed(e: AnActionEvent) {
              GraphQlLogger.logCodyEvent(project, "recipe:" + recipe.id, "clicked")
              val editorManager = FileEditorManager.getInstance(project)
              CodyEditorFactoryListener.Util.informAgentAboutEditorChange(
                      editorManager.selectedTextEditor)
              sendMessage(recipe.title, recipe.id)
            }
          }
      actionManager.registerAction(actionId, action)
      group.addAction(action)
    }
  }

  @RequiresEdt
  private fun createRecipeButton(text: String): JButton {
    val button = JButton(text)
    button.alignmentX = Component.CENTER_ALIGNMENT
    button.maximumSize = Dimension(Int.MAX_VALUE, button.getPreferredSize().height)
    val buttonUI = DarculaButtonUI.createUI(button) as ButtonUI
    button.setUI(buttonUI)
    return button
  }
}
