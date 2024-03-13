package com.sourcegraph.cody.agent.protocol

enum class CodyTaskState(val value: String) {
  idle("idle"),
  working("working"),
  inserting("inserting"),
  applying("applying"),
  formatting("formatting"),
  applied("applied"),
  finished("finished"),
  error("error"),
  pending("pending")
}

val CodyTaskState.isTerminal
  get() =
      when (this) {
        CodyTaskState.finished,
        CodyTaskState.error -> true
        else -> false
      }
