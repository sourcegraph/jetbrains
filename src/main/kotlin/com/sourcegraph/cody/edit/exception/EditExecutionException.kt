package com.sourcegraph.cody.edit.exception

import com.sourcegraph.cody.edit.fixupActions.FixupUndoableAction

class EditExecutionException(action: FixupUndoableAction, cause: Throwable) :
    RuntimeException(cause) {
  override val message: String = "Edit application failed for $action"
}
