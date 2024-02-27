package com.sourcegraph.common.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import javax.swing.Icon

/**
 * Since IJ 2024 every action which overrides `update` method needs to explicitly declare the thread
 * update runs on. This class extends overrides `getActionUpdateThread` to declare it runs on BGT
 * thread. For the details please refer to the `ActionUpdateThread` class.
 */
abstract class DumbAwareBGTAction : DumbAwareAction {

  constructor() : super()

  constructor(icon: Icon?) : super(icon)

  constructor(text: @NlsActions.ActionText String?) : super(text)

  constructor(
      text: @NlsActions.ActionText String?,
      description: @NlsActions.ActionDescription String?,
      icon: Icon?
  ) : super(text, description, icon)

  /**
   * 'ActionUpdateThread' is available only since 222.3345.118 but the module is targeted
   * for221.5080.210+. It may lead to compatibility problems with IDEs prior to 222.3345.118, so we
   * need to ensure everything works fine using manual QA. We can remove the suppression after we
   * drop support for IJ versions older than 222.3345.118 *
   */
  @Suppress("MissingRecentApi")
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
