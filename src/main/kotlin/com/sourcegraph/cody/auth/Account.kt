package com.sourcegraph.cody.auth

import java.util.UUID
import org.jetbrains.annotations.Nls

/**
 * Base class to represent an account for some external system Properties are abstract to allow
 * marking them with persistence annotations
 *
 * Generally supposed to be used as means of distinguishing multiple credentials from PSafe
 *
 * @property id an internal unique identifier of an account
 * @property name short display name for an account to be shown to a user (login/username/email)
 */
abstract class Account {

  abstract val id: String

  @get:Nls abstract val name: String

  final override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Account) return false
    return id == other.id
  }

  final override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    fun generateId() = UUID.randomUUID().toString()
  }
}
