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

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
