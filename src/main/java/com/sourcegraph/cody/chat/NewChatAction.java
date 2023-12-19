package com.sourcegraph.cody.chat;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.sourcegraph.cody.CodyToolWindowContent;
import com.sourcegraph.cody.history.HistoryService;
import com.sourcegraph.common.ErrorNotification;
import org.jetbrains.annotations.NotNull;

public class NewChatAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      displayUnableToResetConversationError();
      return;
    }
    var codyToolWindowContent = CodyToolWindowContent.Companion.getInstance(project);
    var chatId = HistoryService.getInstance().startChat();
    codyToolWindowContent.changeChatTo(chatId);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      CodyToolWindowContent codyToolWindowContent =
          CodyToolWindowContent.Companion.getInstance(project);
      e.getPresentation().setVisible(codyToolWindowContent.isChatVisible());
    }
  }

  private static void displayUnableToResetConversationError() {
    ErrorNotification.INSTANCE.show(
        null,
        "Unable to start a new chat with Cody. Please try again or reach out to us at support@sourcegraph.com.");
  }
}
