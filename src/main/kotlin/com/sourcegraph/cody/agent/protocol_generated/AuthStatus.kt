/*
 * Generated file - DO NOT EDIT MANUALLY
 * They are copied from the cody agent project using the copyProtocol gradle task.
 * This is only a temporary solution before we fully migrate to generated protocol messages.
 */
@file:Suppress("FunctionName", "ClassName", "unused", "EnumEntryName", "UnusedImport")
package com.sourcegraph.cody.agent.protocol_generated;

data class AuthStatus(
  val username: String,
  val endpoint: String? = null,
  val isDotCom: Boolean,
  val isLoggedIn: Boolean,
  val isFireworksTracingEnabled: Boolean,
  val showInvalidAccessTokenError: Boolean,
  val authenticated: Boolean,
  val hasVerifiedEmail: Boolean,
  val requiresVerifiedEmail: Boolean,
  val siteHasCodyEnabled: Boolean,
  val siteVersion: String,
  val codyApiVersion: Long,
  val configOverwrites: CodyLLMSiteConfiguration? = null,
  val showNetworkError: Boolean? = null,
  val primaryEmail: String,
  val displayName: String? = null,
  val avatarURL: String,
  val userCanUpgrade: Boolean,
  val isOfflineMode: Boolean? = null,
)

