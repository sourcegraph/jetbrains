package com.sourcegraph.cody.agent.protocol

enum class CodyTaskState(val value: Int) {
    idle(1),
    working(2), 
    inserting(3),
    applying(4),
    formatting(5),
    applied(6),
    finished(7),
    error(8),
    pending(9)
}

val CodyTaskState.isTerminal
  get() = when(this) {
    CodyTaskState.finished,
    CodyTaskState.error -> true 
    else -> false
  }
