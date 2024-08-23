package com.sourcegraph.cody.auth

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.sourcegraph.cody.config.SourcegraphServerPath
import com.sourcegraph.config.ConfigUtil
import java.util.UUID

@Tag("account")
data class CodyAccount(
    @Property(style = Property.Style.ATTRIBUTE, surroundWithTag = false)
    var server: SourcegraphServerPath = SourcegraphServerPath.from(ConfigUtil.DOTCOM_URL, ""),
    @Attribute("id") var id: String = generateId(),
) {

  fun getToken(): String? = passwordSafe.get(credentialAttributes())?.getPasswordAsString()

  fun saveToken(token: String?) {
    val credentials = token?.let { Credentials(id, it) }
    passwordSafe.set(credentialAttributes(), credentials)
  }

  private fun credentialAttributes() =
      CredentialAttributes(generateServiceName(ConfigUtil.SERVICE_DISPLAY_NAME, id))

  fun isDotcomAccount(): Boolean = server.url.lowercase().startsWith(ConfigUtil.DOTCOM_URL)

  fun isEnterpriseAccount(): Boolean = isDotcomAccount().not()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CodyAccount) return false
    return id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    fun generateId() = UUID.randomUUID().toString()

    private val passwordSafe
      get() = PasswordSafe.instance
  }

  override fun toString(): String = server.toString()
}
