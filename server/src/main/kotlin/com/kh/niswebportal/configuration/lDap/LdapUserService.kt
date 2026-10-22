package com.kh.niswebportal.configuration.lDap
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.filter.EqualsFilter
import org.springframework.ldap.support.LdapUtils
import org.springframework.stereotype.Service

@Service
class LdapUserService(
    private val ldapTemplate: LdapTemplate
) {
    fun authenticate(username: String, password: String): Boolean {
        return try {
            val accountName = if (username.contains("@")) {
                username.substringBefore("@")
            } else {
                username
            }

            val filter = EqualsFilter("sAMAccountName", accountName)

            ldapTemplate.authenticate(
                LdapUtils.emptyLdapName(),
                filter.encode(),
                password
            )
        } catch (e: Exception) {
            println("LDAP Authentication failed: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}