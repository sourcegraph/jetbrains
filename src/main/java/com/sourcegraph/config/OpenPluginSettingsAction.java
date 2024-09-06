package com.sourcegraph.config;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.util.NlsActions;
import com.sourcegraph.cody.config.ui.AccountConfigurable;
import com.sourcegraph.common.ui.DumbAwareEDTAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpenPluginSettingsAction extends DumbAwareEDTAction {
  public OpenPluginSettingsAction() {
    super();
  }

  public OpenPluginSettingsAction(@Nullable @NlsActions.ActionText String text) {
    super(text);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    ShowSettingsUtil.getInstance()
        .showSettingsDialog(event.getProject(), AccountConfigurable.class);
  }
}
