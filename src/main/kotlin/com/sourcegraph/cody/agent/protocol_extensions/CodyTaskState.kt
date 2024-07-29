package com.sourcegraph.cody.agent.protocol_extensions

object CodyTaskStateExt {
  // TODO: this is just temporary until CODY-2882 is fixed
  @Suppress("EnumValuesSoftDeprecate")
  enum class AllowedValues(val value: String) {
    Idle("Idle"),
    Working("Working"),
    Inserting("Inserting"),
    Applying("Applying"),
    Formatting("Formatting"),
    Applied("Applied"),
    Finished("Finished"),
    Error("Error"),
    Pending("Pending");

    companion object {
      fun fromValue(value: String): AllowedValues? {
        return values().firstOrNull { it.value == value }
      }
    }
  }
}
