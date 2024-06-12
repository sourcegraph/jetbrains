package com.sourcegraph.cody.chat

open class PromptHistory(private val capacity: Int) {
  private val history = mutableListOf<String>()
  private var currentIndex = -1

  fun add(item: String) {
    history.add(item)
    if (history.size > capacity) {
      history.removeAt(0)
    }
    resetHistory()
  }

  fun getPrevious(): String? {
    if (currentIndex > 0) {
      currentIndex--
      return history[currentIndex]
    }
    return if (history.size == 1) history[0] else null
  }

  fun getNext(): String? {
    if (currentIndex < history.size - 1) {
      currentIndex++
      return history[currentIndex]
    }
    return if (history.size == 1) history[0] else null
  }

  fun getCurrent(): String? {
    return if (history.size > 0 && currentIndex < history.size) {
      history[currentIndex]
    } else {
      null
    }
  }

  fun isNotEmpty() = history.isNotEmpty()

  fun resetHistory() {
    currentIndex = history.size - 1
  }
}
