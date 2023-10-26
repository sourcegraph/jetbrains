package com.sourcegraph.cody;

import static com.intellij.ui.SimpleTextAttributes.STYLE_PLAIN;
import static java.awt.event.KeyEvent.*;
import static javax.swing.KeyStroke.getKeyStroke;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.sourcegraph.cody.agent.CodyAgent;
import com.sourcegraph.cody.agent.CodyAgentManager;
import com.sourcegraph.cody.agent.CodyAgentServer;
import com.sourcegraph.cody.agent.protocol.RecipeInfo;
import com.sourcegraph.cody.api.Speaker;
import com.sourcegraph.cody.chat.*;
import com.sourcegraph.cody.chat.Chat;
import com.sourcegraph.cody.chat.ChatMessage;
import com.sourcegraph.cody.chat.ChatUIConstants;
import com.sourcegraph.cody.chat.CodyOnboardingGuidancePanel;
import com.sourcegraph.cody.chat.ContextFilesMessage;
import com.sourcegraph.cody.chat.MessagePanel;
import com.sourcegraph.cody.chat.SignInWithSourcegraphPanel;
import com.sourcegraph.cody.config.CodyAccount;
import com.sourcegraph.cody.config.CodyApplicationSettings;
import com.sourcegraph.cody.config.CodyAuthenticationManager;
import com.sourcegraph.cody.context.ContextMessage;
import com.sourcegraph.cody.context.EmbeddingStatusView;
import com.sourcegraph.cody.ui.AutoGrowingTextArea;
import com.sourcegraph.cody.ui.ChatScrollPane;
import com.sourcegraph.cody.vscode.CancellationToken;
import com.sourcegraph.telemetry.GraphQlLogger;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.plaf.ButtonUI;
import javax.swing.text.DefaultEditorKit;
import org.jetbrains.annotations.NotNull;

public class CodyToolWindowContent implements UpdatableChat {
  public static final String ONBOARDING_PANEL = "onboardingPanel";
  public static final int CHAT_PANEL_INDEX = 0;
  public static final int SIGN_IN_PANEL_INDEX = 1;
  public static final int ONBOARDING_PANEL_INDEX = 2;
  public static Logger logger = Logger.getInstance(CodyToolWindowContent.class);
  public static final String SING_IN_WITH_SOURCEGRAPH_PANEL = "singInWithSourcegraphPanel";
  private static final int CHAT_TAB_INDEX = 0;
  private static final int RECIPES_TAB_INDEX = 1;
  private static final int CHAT_MESSAGE_HISTORY_CAPACITY = 100;
  private final @NotNull CardLayout allContentLayout = new CardLayout();
  private final @NotNull JPanel allContentPanel = new JPanel(allContentLayout);
  private final @NotNull JBTabbedPane tabbedPane = new JBTabbedPane();
  private final @NotNull JPanel messagesPanel = new JPanel();
  private final @NotNull JBTextArea promptInput;
  private final @NotNull JButton sendButton;
  private final @NotNull Project project;
  private CancellationToken inProgressChat = new CancellationToken();
  private final JButton stopGeneratingButton =
      new JButton("Stop generating", IconUtil.desaturate(AllIcons.Actions.Suspend));
  private final @NotNull JBPanelWithEmptyText recipesPanel;
  public final EmbeddingStatusView embeddingStatusView;
  private boolean isChatVisible = false;
  private CodyOnboardingGuidancePanel codyOnboardingGuidancePanel;
  private final @NotNull CodyChatMessageHistory chatMessageHistory =
      new CodyChatMessageHistory(CHAT_MESSAGE_HISTORY_CAPACITY);
  private boolean isInHistoryMode = true;

  public CodyToolWindowContent(@NotNull Project project) {
    this.project = project;
    // Tabs
    @NotNull JPanel contentPanel = new JPanel();
    tabbedPane.insertTab("Chat", null, contentPanel, null, CHAT_TAB_INDEX);
    recipesPanel = new JBPanelWithEmptyText(new GridLayout(0, 1));
    recipesPanel.setLayout(new BoxLayout(recipesPanel, BoxLayout.Y_AXIS));
    tabbedPane.insertTab("Commands", null, recipesPanel, null, RECIPES_TAB_INDEX);

    // Initiate filling recipes panel in the background
    refreshRecipes();

    // Chat panel
    messagesPanel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    ChatScrollPane chatPanel = new ChatScrollPane(messagesPanel);

    // Controls panel
    JPanel controlsPanel = new JPanel();
    controlsPanel.setLayout(new BorderLayout());
    controlsPanel.setBorder(new EmptyBorder(JBUI.insets(0, 14, 14, 14)));
    JPanel promptPanel = new JPanel(new BorderLayout());
    sendButton = createSendButton(this.project);
    AutoGrowingTextArea autoGrowingTextArea = new AutoGrowingTextArea(3, 9, promptPanel);
    promptInput = autoGrowingTextArea.getTextArea();
    /* Submit on enter */
    KeyboardShortcut JUST_ENTER = new KeyboardShortcut(getKeyStroke(VK_ENTER, 0), null);
    KeyboardShortcut UP = new KeyboardShortcut(getKeyStroke(VK_UP, 0), null);
    KeyboardShortcut DOWN = new KeyboardShortcut(getKeyStroke(VK_DOWN, 0), null);

    ShortcutSet DEFAULT_SUBMIT_ACTION_SHORTCUT = new CustomShortcutSet(JUST_ENTER);
    ShortcutSet POP_UPPER_MESSAGE_ACTION_SHORTCUT = new CustomShortcutSet(UP);
    ShortcutSet POP_LOWER_MESSAGE_ACTION_SHORTCUT = new CustomShortcutSet(DOWN);

    AnAction upperMessageAction =
        new DumbAwareAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (isInHistoryMode) {
              chatMessageHistory.popUpperMessage(promptInput);
            } else {
              Action defaultAction = promptInput.getActionMap().get(DefaultEditorKit.upAction);
              defaultAction.actionPerformed(null);
            }
          }
        };
    AnAction lowerMessageAction =
        new DumbAwareAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (isInHistoryMode) {
              chatMessageHistory.popLowerMessage(promptInput);
            } else {
              Action defaultAction = promptInput.getActionMap().get(DefaultEditorKit.downAction);
              defaultAction.actionPerformed(null);
            }
          }
        };

    AnAction sendMessageAction =
        new DumbAwareAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (!promptInput.getText().isEmpty()) {
              sendChatMessage(project);
            }
          }
        };
    sendMessageAction.registerCustomShortcutSet(DEFAULT_SUBMIT_ACTION_SHORTCUT, promptInput);
    upperMessageAction.registerCustomShortcutSet(POP_UPPER_MESSAGE_ACTION_SHORTCUT, promptInput);
    lowerMessageAction.registerCustomShortcutSet(POP_LOWER_MESSAGE_ACTION_SHORTCUT, promptInput);
    promptInput.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyReleased(KeyEvent e) {
            int keyCode = e.getKeyCode();

            if (keyCode != VK_UP && keyCode != VK_DOWN) {
              isInHistoryMode = promptInput.getText().isEmpty();
            }
          }
        });
    // Enable/disable the send button based on whether promptInput is empty
    promptInput
        .getDocument()
        .addDocumentListener(
            new DocumentAdapter() {
              @Override
              protected void textChanged(@NotNull DocumentEvent e) {
                sendButton.setEnabled(!promptInput.getText().isEmpty());
              }
            });

    promptPanel.add(autoGrowingTextArea.getScrollPane(), BorderLayout.CENTER);
    promptPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

    JPanel stopGeneratingButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
    stopGeneratingButtonPanel.setPreferredSize(
        new Dimension(Short.MAX_VALUE, stopGeneratingButton.getPreferredSize().height + 10));
    stopGeneratingButton.addActionListener(
        e -> {
          inProgressChat.abort();
          stopGeneratingButton.setVisible(false);
          sendButton.setEnabled(true);
        });
    stopGeneratingButton.setVisible(false);
    stopGeneratingButtonPanel.add(stopGeneratingButton);
    stopGeneratingButtonPanel.setOpaque(false);
    controlsPanel.add(promptPanel, BorderLayout.NORTH);
    controlsPanel.add(sendButton, BorderLayout.EAST);
    JPanel lowerPanel = new JPanel(new BorderLayout());
    Color borderColor = ColorUtil.brighter(UIUtil.getPanelBackground(), 3);
    Border topBorder = BorderFactory.createMatteBorder(1, 0, 0, 0, borderColor);
    lowerPanel.setBorder(topBorder);
    lowerPanel.setLayout(new BoxLayout(lowerPanel, BoxLayout.Y_AXIS));
    lowerPanel.add(stopGeneratingButtonPanel);
    lowerPanel.add(controlsPanel);

    embeddingStatusView = new EmbeddingStatusView(project);
    embeddingStatusView.setBorder(topBorder);
    lowerPanel.add(embeddingStatusView);

    // Main content panel
    contentPanel.setLayout(new BorderLayout(0, 0));
    contentPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

    contentPanel.add(chatPanel, BorderLayout.CENTER);
    contentPanel.add(lowerPanel, BorderLayout.SOUTH);

    tabbedPane.addChangeListener(e -> this.focusPromptInput());

    SignInWithSourcegraphPanel singInWithSourcegraphPanel = new SignInWithSourcegraphPanel(project);
    allContentPanel.add(tabbedPane, "tabbedPane", CHAT_PANEL_INDEX);
    allContentPanel.add(
        singInWithSourcegraphPanel, SING_IN_WITH_SOURCEGRAPH_PANEL, SIGN_IN_PANEL_INDEX);
    allContentLayout.show(allContentPanel, SING_IN_WITH_SOURCEGRAPH_PANEL);
    updateVisibilityOfContentPanels();
    // Add welcome message
    addWelcomeMessage();
  }

  @RequiresEdt
  public void refreshRecipes() {
    recipesPanel.removeAll();
    recipesPanel.getEmptyText().setText("Loading recipes...");
    recipesPanel.revalidate();
    recipesPanel.repaint();

    CodyAgentServer server = CodyAgent.getServer(project);
    if (server == null) {
      setRecipesPanelError();
      return;
    }

    ApplicationManager.getApplication()
        .executeOnPooledThread( // Non-blocking data fetch
            () -> {
              try {
                server
                    .recipesList()
                    .thenAccept(
                        (List<RecipeInfo> recipes) ->
                            ApplicationManager.getApplication()
                                .invokeLater(
                                    () -> updateUIWithRecipeList(recipes))); // Update on EDT
              } catch (Exception e) {
                logger.warn("Error fetching recipes from agent", e);
                // Update on EDT
                ApplicationManager.getApplication().invokeLater(this::setRecipesPanelError);
              }
            });
  }

  @RequiresEdt
  private void setRecipesPanelError() {
    StatusText emptyText = recipesPanel.getEmptyText();

    emptyText.setText(
        "Error fetching recipes. Check your connection. If the problem persists, please contact support.");
    emptyText.appendLine(
        "Retry",
        new SimpleTextAttributes(STYLE_PLAIN, JBUI.CurrentTheme.Link.Foreground.ENABLED),
        __ -> refreshRecipes());
  }

  @RequiresEdt
  private void updateUIWithRecipeList(@NotNull List<RecipeInfo> recipes) {
    // we don't want to display recipes with ID "chat-question" and "code-question"
    var excludedRecipeIds = List.of("chat-question", "code-question", "translate-to-language");
    var recipesToDisplay =
        recipes.stream()
            .filter(recipe -> !excludedRecipeIds.contains(recipe.id))
            .collect(Collectors.toList());

    fillRecipesPanel(recipesToDisplay);
    fillContextMenu(recipesToDisplay);
  }

  @RequiresEdt
  private void fillRecipesPanel(@NotNull List<RecipeInfo> recipes) {
    recipesPanel.removeAll();

    // Loop on recipes and add a button for each item
    for (RecipeInfo recipe : recipes) {
      if (recipe.id == null || recipe.title == null) {
        continue;
      }
      JButton recipeButton = createRecipeButton(recipe.title);
      recipeButton.addActionListener(
          e -> {
            GraphQlLogger.logCodyEvent(this.project, "recipe:" + recipe.id, "clicked");
            sendMessage(this.project, recipe.title, recipe.id);
          });
      recipesPanel.add(recipeButton);
    }
  }

  private void fillContextMenu(@NotNull List<RecipeInfo> recipes) {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = (DefaultActionGroup) actionManager.getAction("CodyEditorActions");

    // Loop on recipes and create an action for each new item
    for (RecipeInfo recipe : recipes) {
      if (recipe.id == null || recipe.title == null) {
        continue;
      }
      String actionId = "cody.recipe." + recipe.id;
      var existingAction = actionManager.getAction(actionId);
      if (existingAction != null) {
        continue;
      }
      var action =
          new DumbAwareAction(recipe.title) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              GraphQlLogger.logCodyEvent(project, "recipe:" + recipe.id, "clicked");
              sendMessage(project, recipe.title, recipe.id);
            }
          };
      actionManager.registerAction(actionId, action);
      group.addAction(action);
    }
  }

  public static @NotNull CodyToolWindowContent getInstance(@NotNull Project project) {
    return project.getService(CodyToolWindowContent.class);
  }

  @RequiresEdt
  private void updateVisibilityOfContentPanels() {
    CodyAuthenticationManager codyAuthenticationManager = CodyAuthenticationManager.getInstance();
    if (codyAuthenticationManager.getAccounts().isEmpty()) {
      allContentLayout.show(allContentPanel, SING_IN_WITH_SOURCEGRAPH_PANEL);
      isChatVisible = false;
      return;
    }
    CodyAccount activeAccount = codyAuthenticationManager.getActiveAccount(project);
    if (!CodyApplicationSettings.getInstance().isOnboardingGuidanceDismissed()) {
      String displayName =
          Optional.ofNullable(activeAccount).map(CodyAccount::getDisplayName).orElse(null);
      CodyOnboardingGuidancePanel newCodyOnboardingGuidancePanel =
          new CodyOnboardingGuidancePanel(displayName);
      newCodyOnboardingGuidancePanel.addMainButtonActionListener(
          e -> {
            CodyApplicationSettings.getInstance().setOnboardingGuidanceDismissed(true);
            updateVisibilityOfContentPanels();
            refreshRecipes();
          });
      if (displayName != null) {
        if (codyOnboardingGuidancePanel != null
            && !displayName.equals(codyOnboardingGuidancePanel.getOriginalDisplayName()))
          try {
            allContentPanel.remove(ONBOARDING_PANEL_INDEX);
          } catch (Throwable ex) {
            // ignore because panel was not created before
          }
      }
      codyOnboardingGuidancePanel = newCodyOnboardingGuidancePanel;
      allContentPanel.add(codyOnboardingGuidancePanel, ONBOARDING_PANEL, ONBOARDING_PANEL_INDEX);
      allContentLayout.show(allContentPanel, ONBOARDING_PANEL);
      isChatVisible = false;
      return;
    }

    allContentLayout.show(allContentPanel, "tabbedPane");
    isChatVisible = true;
  }

  @NotNull
  private JButton createRecipeButton(@NotNull String text) {
    JButton button = new JButton(text);
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
    ButtonUI buttonUI = (ButtonUI) DarculaButtonUI.createUI(button);
    button.setUI(buttonUI);
    return button;
  }

  private void addWelcomeMessage() {
    String welcomeText =
        "Hello! I'm Cody. I can write code and answer questions for you. See [Cody documentation](https://docs.sourcegraph.com/cody) for help and tips.";
    addMessageToChat(new ChatMessage(Speaker.ASSISTANT, welcomeText), false);
  }

  @NotNull
  private JButton createSendButton(@NotNull Project project) {
    JButton sendButton = new JButton("Send");
    sendButton.putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, Boolean.TRUE);
    ButtonUI buttonUI = (ButtonUI) DarculaButtonUI.createUI(sendButton);
    sendButton.setUI(buttonUI);
    sendButton.addActionListener(
        e -> {
          GraphQlLogger.logCodyEvent(this.project, "recipe:chat-question", "clicked");
          sendChatMessage(project);
        });
    return sendButton;
  }

  public synchronized void addMessageToChat(
      @NotNull ChatMessage message, boolean shouldDisplayBlinkingCursor) {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              // Bubble panel
              MessagePanel messagePanel =
                  new MessagePanel(
                      message,
                      project,
                      messagesPanel,
                      ChatUIConstants.ASSISTANT_MESSAGE_GRADIENT_WIDTH);
              addComponentToChat(messagePanel);
              ensureBlinkingCursorIsNotDisplayed();
              if (shouldDisplayBlinkingCursor) {
                messagesPanel.add(BlinkingCursorComponent.instance);
              }
            });
  }

  public void addComponentToChat(@NotNull JPanel messageContent) {
    var wrapperPanel = new JPanel();
    wrapperPanel.setLayout(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    // Chat message
    wrapperPanel.add(messageContent, VerticalFlowLayout.TOP);
    messagesPanel.add(wrapperPanel);
    messagesPanel.revalidate();
    messagesPanel.repaint();
  }

  @Override
  public void activateChatTab() {
    this.tabbedPane.setSelectedIndex(CHAT_TAB_INDEX);
  }

  public synchronized void updateLastMessage(@NotNull ChatMessage message) {
    ApplicationManager.getApplication()
        .invokeLater(
            () ->
                Optional.of(messagesPanel)
                    .filter(mp -> mp.getComponentCount() > 0)
                    .map(mp -> mp.getComponent(mp.getComponentCount() - 1))
                    .filter(component -> component instanceof JPanel)
                    .map(component -> (JPanel) component)
                    .map(lastWrapperPanel -> lastWrapperPanel.getComponent(0))
                    .filter(component -> component instanceof MessagePanel)
                    .map(component -> (MessagePanel) component)
                    .ifPresent(lastMessage -> lastMessage.updateContentWith(message)));
  }

  private void startMessageProcessing() {
    this.inProgressChat = new CancellationToken();
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              stopGeneratingButton.setVisible(true);
              sendButton.setEnabled(false);
            });
  }

  @Override
  public void finishMessageProcessing() {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              stopGeneratingButton.setVisible(false);
              sendButton.setEnabled(true);
              ensureBlinkingCursorIsNotDisplayed();
            });
  }

  @Override
  public void resetConversation() {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              stopGeneratingButton.setVisible(false);
              sendButton.setEnabled(true);
              messagesPanel.removeAll();
              addWelcomeMessage();
              messagesPanel.revalidate();
              messagesPanel.repaint();
              chatMessageHistory.clearHistory();
              CodyAgent.getInitializedServer(project).thenAccept(CodyAgentServer::transcriptReset);
              ensureBlinkingCursorIsNotDisplayed();
            });
  }

  private void ensureBlinkingCursorIsNotDisplayed() {
    Arrays.stream(messagesPanel.getComponents())
        .filter(x -> x == BlinkingCursorComponent.instance)
        .forEach(x -> messagesPanel.remove(BlinkingCursorComponent.instance));
  }

  @Override
  @RequiresEdt
  public void refreshPanelsVisibility() {
    this.updateVisibilityOfContentPanels();
  }

  @Override
  public boolean isChatVisible() {
    return this.isChatVisible;
  }

  @RequiresEdt
  private void sendChatMessage(@NotNull Project project) {
    String text = promptInput.getText();
    chatMessageHistory.messageSent(promptInput);
    sendMessage(project, text, "chat-question");
    promptInput.setText("");
  }

  @RequiresEdt
  private void sendMessage(
      @NotNull Project project, @NotNull String message, @NotNull String recipeId) {
    if (!sendButton.isEnabled()) {
      return;
    }

    startMessageProcessing();

    String displayText = XmlStringUtil.escapeString(message);
    ChatMessage humanMessage = new ChatMessage(Speaker.HUMAN, message, displayText);
    addMessageToChat(humanMessage, true);
    activateChatTab();

    // This cannot run on EDT (Event Dispatch Thread) because it may block for a long time.
    // Also, if we did the back-end call in the main thread and then waited, we wouldn't see the
    // messages streamed back to us.
    ApplicationManager.getApplication()
        .executeOnPooledThread(
            () -> {
              Chat chat = new Chat();
              CodyAgentManager.tryRestartingAgentIfNotRunning(project);
              if (CodyAgent.isConnected(project)) {
                try {
                  chat.sendMessageViaAgent(
                      CodyAgent.getClient(project),
                      CodyAgent.getInitializedServer(project),
                      humanMessage,
                      recipeId,
                      this,
                      this.inProgressChat);
                } catch (Exception e) {
                  logger.warn("Error sending message '" + humanMessage + "' to chat", e);
                }
              } else {
                logger.warn("Agent is disabled, can't use chat.");
                this.addMessageToChat(
                    new ChatMessage(
                        Speaker.ASSISTANT,
                        "Cody is not able to reply at the moment. "
                            + "This is a bug, please report an issue to https://github.com/sourcegraph/sourcegraph/issues/new?template=jetbrains.md "
                            + "and include as many details as possible to help troubleshoot the problem."),
                    false);
                this.finishMessageProcessing();
              }
              GraphQlLogger.logCodyEvent(this.project, "recipe:chat-question", "executed");
            });
  }

  @Override
  public void displayUsedContext(@NotNull List<ContextMessage> contextMessages) {
    if (contextMessages.isEmpty()) {
      // Do nothing when there are no context files. It's normal that some answers have no context
      // files.
      return;
    }

    ContextFilesMessage contextFilesMessage = new ContextFilesMessage(contextMessages);
    var messageContentPanel = new JPanel(new BorderLayout());
    messageContentPanel.add(contextFilesMessage);
    this.addComponentToChat(messageContentPanel);
  }

  public @NotNull JComponent getContentPanel() {
    return allContentPanel;
  }

  public void focusPromptInput() {
    if (tabbedPane.getSelectedIndex() == CHAT_TAB_INDEX) {
      promptInput.requestFocusInWindow();
      int textLength = promptInput.getDocument().getLength();
      promptInput.setCaretPosition(textLength);
    }
  }

  public JComponent getPreferredFocusableComponent() {
    return promptInput;
  }
}
