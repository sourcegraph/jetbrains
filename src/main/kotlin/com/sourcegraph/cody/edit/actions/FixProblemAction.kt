package com.sourcegraph.cody.edit.actions

class FixProblemAction :
    NonInteractiveEditCommandAction({ editor, fixupService ->
      fixupService.startFixProblem(editor)
    }) {
  companion object {
    const val ID: String = "cody.fixProblemAction"
  }
}
