package com.sourcegraph.cody.ui

class ConditionalVisibilityButton(text: String) : TransparentButton(text) {

  var visibilityAllowed: Boolean = true
    set(value) {
      field = value
      if (!value) {
        super.setVisible(false)
      }
    }

  override fun setVisible(value: Boolean) {
    if ((value && visibilityAllowed) // either make visible if visibility allowed
    || (!value) // or make invisible
    ) {
      super.setVisible(value)
    }
  }
}
