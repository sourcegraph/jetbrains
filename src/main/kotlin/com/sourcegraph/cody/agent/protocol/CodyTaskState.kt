package com.sourcegraph.cody.agent.protocol

enum class CodyTaskState(val id: Int) {
  Idle(0),
  Working(1),
  Inserting(2),
  Applying(3),
  Formatting(4),
  Applied(5),
  Finished(6),
  Error(7),
  Pending(8)
}


val CodyTaskState.isTerminal
  get() =
      when (this) {
        CodyTaskState.Finished,
        CodyTaskState.Error -> true
        else -> false
      }
