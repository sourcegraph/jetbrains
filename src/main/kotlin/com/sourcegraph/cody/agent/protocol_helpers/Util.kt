package com.sourcegraph.cody.agent.protocol_helpers

inline fun <reified T : Enum<T>> String.toEnumIgnoreCase(): T? {
  return enumValues<T>().firstOrNull { it.name.equals(this, ignoreCase = true) }
}
