package com.sourcegraph.config;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.sourcegraph.Icons;
import com.sourcegraph.cody.CodyToolWindowFactory;
import com.sourcegraph.cody.config.CodyAccount;
import com.sourcegraph.cody.config.CodyAccountManager;
import com.sourcegraph.cody.config.CodyApplicationSettings;
import com.sourcegraph.cody.config.CodyAuthenticationManager;
import com.sourcegraph.cody.initialization.Activity;
import com.sourcegraph.cody.statusbar.CodyManageAccountsAction;
import com.sourcegraph.common.NotificationGroups;
import com.sourcegraph.common.ui.DumbAwareEDTAction;
import org.jetbrains.annotations.NotNull;

public class CodyAuthNotificationActivity implements Activity {

  @Override
  public void runActivity(@NotNull Project project) {
    CodyAccount activeAccount = CodyAuthenticationManager.getInstance(project).getAccount();
    CodyAccountManager service =
        ApplicationManager.getApplication().getService(CodyAccountManager.class);

    if (activeAccount != null) {
      String token = service.findCredentials(activeAccount);
      if (token == null) {
        showMissingTokenNotification();
      }
    }

    if (!CodyApplicationSettings.getInstance().isGetStartedNotificationDismissed()
        && activeAccount == null) {
      showOpenCodySidebarNotification(project);
    }
  }

  private void showOpenCodySidebarNotification(@NotNull Project project) {
    // Display notification
    Notification notification =
        new Notification(
            NotificationGroups.CODY_AUTH,
            "Open Cody sidebar to get started",
            "",
            NotificationType.WARNING);

    AnAction openCodySidebar =
        new DumbAwareEDTAction("Open Cody") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            notification.expire();
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow toolWindow =
                toolWindowManager.getToolWindow(CodyToolWindowFactory.TOOL_WINDOW_ID);
            if (toolWindow != null) {
              toolWindow.setAvailable(true, null);
              toolWindow.activate(null);
            }
          }
        };

    AnAction neverShowAgainAction =
        new DumbAwareEDTAction("Never Show Again") {
          @Override
          public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            notification.expire();
            CodyApplicationSettings.getInstance().setGetStartedNotificationDismissed(true);
          }
        };
    notification.setIcon(Icons.CodyLogo);
    notification.addAction(openCodySidebar);
    notification.addAction(neverShowAgainAction);
    Notifications.Bus.notify(notification);
  }

  private void showMissingTokenNotification() {
    // Display notification
    Notification notification =
        new Notification(
            NotificationGroups.CODY_AUTH, "Missing access token", "", NotificationType.WARNING);

    notification.setIcon(Icons.CodyLogo);
    notification.addAction(new CodyManageAccountsAction());
    Notifications.Bus.notify(notification);
  }
}
