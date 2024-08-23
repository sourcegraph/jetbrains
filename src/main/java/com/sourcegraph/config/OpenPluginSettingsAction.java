package com.sourcegraph.config;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.sourcegraph.cody.config.ui.CodyConfigurable;
import com.sourcegraph.common.ui.DumbAwareEDTAction;
import org.jetbrains.annotations.NotNull;

public class OpenPluginSettingsAction extends DumbAwareEDTAction {
  public OpenPluginSettingsAction() {
    super();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    ShowSettingsUtil.getInstance().showSettingsDialog(event.getProject(), CodyConfigurable.class);
  }
}
